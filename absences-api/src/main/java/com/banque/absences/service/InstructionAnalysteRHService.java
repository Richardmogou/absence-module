package com.banque.absences.service;

import com.banque.absences.domain.AbsenceEvent;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.domain.Validation;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.JustificatifDocumentRepository;
import com.banque.absences.repository.ValidationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstructionAnalysteRHService {

    private static final Set<TypeAbsence> TYPES_AVEC_JUSTIFICATIF = Set.of(
            TypeAbsence.CONGE_MALADIE,
            TypeAbsence.PERMISSION,
            TypeAbsence.CONGE_MATERNITE
    );

    private final DemandeAbsenceRepository demandeAbsenceRepository;
    private final JustificatifDocumentRepository justificatifDocumentRepository;
    private final AbsenceStateMachine stateMachine;
    private final DocumentMiseEnCongeService documentMiseEnCongeService;
    private final com.banque.absences.repository.EtapeDemandeSnapshotRepository etapeDemandeSnapshotRepository;
    private final ValidationRepository validationRepository;

    @Transactional
    @PreAuthorize("hasRole('ANALYSTE_RH')")
    public DemandeAbsence instruire(UUID demandeId, java.time.LocalDate dateDebut) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));

        if (demande.getStatut() != StatutDemande.EN_INSTRUCTION_ANALYSTE_RH) {
            throw new TransitionIllegaleException(
                    "La demande doit être au statut EN_INSTRUCTION_ANALYSTE_RH pour être instruite — statut actuel : "
                            + demande.getStatut());
        }

        if (TYPES_AVEC_JUSTIFICATIF.contains(demande.getType())
                && !justificatifDocumentRepository.existsByDemandeId(demandeId)) {
            throw new JustificatifRequisException(
                    "Le justificatif est obligatoire avant transmission a la DRH");
        }

        // Congé maternité : c'est l'analyste RH qui fixe la date de début à l'instruction.
        // Le système calcule alors la date de fin (+14 semaines) et la durée (98 jours calendaires).
        if (demande.getType() == TypeAbsence.CONGE_MATERNITE) {
            if (dateDebut == null) {
                throw new IllegalArgumentException(
                        "La date de début est obligatoire pour instruire un congé maternité");
            }
            demande.setDateDebut(dateDebut);
            demande.setDateFin(dateDebut.plusWeeks(14));
            demande.setNombreJours(98);
        }

        // Enregistrer le validateur Analyste RH sur son étape, ET journaliser sa décision.
        // Le snapshot porte l'état courant du circuit ; `validation` porte l'historique. Écrire
        // seulement le snapshot laissait l'instruction absente de tout journal durable — alors
        // que c'est ici que se fixe la date de début d'un congé maternité.
        String currentUser = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        etapeDemandeSnapshotRepository.findByDemandeIdOrderByOrdreAsc(demandeId).stream()
                .filter(e -> "ANALYSTE_RH".equals(e.getRoleHabilite()))
                .findFirst()
                .ifPresent(etape -> {
                    etape.setValidateurIdentifiantExterne(currentUser);
                    etape.setStatut(com.banque.absences.domain.EtapeDemandeSnapshot.StatutEtape.VALIDEE);
                    etape.setDateTraitement(java.time.LocalDateTime.now());
                    etapeDemandeSnapshotRepository.save(etape);

                    // Journalisé dans le même ifPresent : la FK validation.etape_snapshot_id est
                    // NOT NULL, il ne peut donc pas exister de ligne sans étape ANALYSTE_RH.
                    Validation validation = new Validation();
                    validation.setDemandeId(demandeId);
                    validation.setEtapeSnapshotId(etape.getId());
                    validation.setValidateurIdentifiantExterne(currentUser);
                    validation.setDecision(Validation.DecisionValidation.INSTRUITE);
                    validation.setDateDecision(java.time.LocalDateTime.now());
                    validationRepository.save(validation);
                });

        stateMachine.sendEvent(demande, AbsenceEvent.TRANSMETTRE);
        demandeAbsenceRepository.save(demande);

        // Générer le document dès l'instruction par l'analyste RH
        documentMiseEnCongeService.genererDocumentMiseEnConge(demandeId);

        return demande;
    }
}
