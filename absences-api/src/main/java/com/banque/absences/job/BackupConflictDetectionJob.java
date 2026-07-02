package com.banque.absences.job;

import com.banque.absences.domain.AbsenceEvent;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.service.AbsenceStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackupConflictDetectionJob {

    private final DemandeAbsenceRepository demandeAbsenceRepository;
    private final AbsenceStateMachine stateMachine;

    private static final Set<StatutDemande> STATUTS_A_VERIFIER = Set.of(
            StatutDemande.SOUMISE,
            StatutDemande.EN_VALIDATION_ETAPE,
            StatutDemande.EN_INSTRUCTION_ANALYSTE_RH,
            StatutDemande.EN_VALIDATION_DRH
    );

    private static final Set<StatutDemande> STATUTS_IGNORES = Set.of(
            StatutDemande.ANNULEE,
            StatutDemande.REJETEE,
            StatutDemande.REJETEE_PAR_LE_SYSTEME
    );

    // Exécution toutes les heures (cron = "0 0 * * * *")
    // Pour des tests rapides, on peut le mettre à "0 * * * * *" (toutes les minutes)
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void detectBackupConflicts() {
        log.info("Démarrage du job de détection des conflits de backup...");

        List<DemandeAbsence> demandesEnCours = demandeAbsenceRepository.findByStatutIn(STATUTS_A_VERIFIER);

        int conflitsDetectes = 0;

        for (DemandeAbsence demande : demandesEnCours) {
            String backupId = demande.getBackupIdentifiantExterne();
            if (backupId == null || backupId.isBlank()) {
                continue;
            }

            List<DemandeAbsence> absencesBackup = demandeAbsenceRepository
                    .findByDemandeurIdentifiantExterneAndStatutNotIn(backupId, STATUTS_IGNORES);

            for (DemandeAbsence absenceBackup : absencesBackup) {
                // Vérifier chevauchement
                if (periodeSeChevauche(demande, absenceBackup, 0)) {
                    // C'est un conflit.
                    boolean isCrossBackup = demande.getDemandeurIdentifiantExterne()
                            .equals(absenceBackup.getBackupIdentifiantExterne());

                    String motifRejet = isCrossBackup
                            ? "Conflit détecté par le système : Votre backup (" + backupId + ") vous a également désigné comme backup sur la même période."
                            : "Conflit détecté par le système : Votre backup (" + backupId + ") est également en absence sur cette période.";

                    log.warn("Conflit détecté pour la demande {}: {}", demande.getId(), motifRejet);

                    // Rejeter la demande
                    demande.setMotifRejetSysteme(motifRejet);
                    demandeAbsenceRepository.save(demande);

                    try {
                        stateMachine.sendEvent(demande, AbsenceEvent.REJETER_SYSTEME);
                    } catch (Exception e) {
                        log.error("Erreur lors du rejet de la demande {}", demande.getId(), e);
                        // Fallback si la transition échoue
                        demande.setStatut(StatutDemande.REJETEE_PAR_LE_SYSTEME);
                        demandeAbsenceRepository.save(demande);
                    }
                    conflitsDetectes++;
                    break; // On a déjà rejeté cette demande, on passe à la suivante
                }
            }
        }

        log.info("Fin du job. {} conflits détectés et rejetés.", conflitsDetectes);
    }

    private boolean periodeSeChevauche(DemandeAbsence a, DemandeAbsence b, int margeJours) {
        if (a.getDateFin() == null || b.getDateFin() == null) {
            // S'il n'y a pas de date de fin, on gère au cas par cas ou on ignore.
            // Pour l'instant on considère que la dateFin est obligatoire ou on utilise dateDebut
            LocalDate finA = a.getDateFin() != null ? a.getDateFin() : a.getDateDebut();
            LocalDate finB = b.getDateFin() != null ? b.getDateFin() : b.getDateDebut();
            
            LocalDate debutA = a.getDateDebut().minusDays(margeJours);
            LocalDate fA = finA.plusDays(margeJours);
            return !(finB.isBefore(debutA) || b.getDateDebut().isAfter(fA));
        }

        LocalDate debutA = a.getDateDebut().minusDays(margeJours);
        LocalDate finA = a.getDateFin().plusDays(margeJours);
        return !(b.getDateFin().isBefore(debutA) || b.getDateDebut().isAfter(finA));
    }
}
