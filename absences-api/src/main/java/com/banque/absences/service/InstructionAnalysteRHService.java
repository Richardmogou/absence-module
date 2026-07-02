package com.banque.absences.service;

import com.banque.absences.domain.AbsenceEvent;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.JustificatifDocumentRepository;
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
            TypeAbsence.MISSION_LONGUE,
            TypeAbsence.CONGE_MATERNITE
    );

    private final DemandeAbsenceRepository demandeAbsenceRepository;
    private final JustificatifDocumentRepository justificatifDocumentRepository;
    private final AbsenceStateMachine stateMachine;

    @Transactional
    @PreAuthorize("hasRole('ANALYSTE_RH')")
    public DemandeAbsence instruire(UUID demandeId) {
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

        stateMachine.sendEvent(demande, AbsenceEvent.TRANSMETTRE);
        return demandeAbsenceRepository.save(demande);
    }
}
