package com.banque.absences.service;

import com.banque.absences.domain.AbsenceEvent;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Machine à états générique pilotée par le snapshot d'étapes.
 *
 * Transitions implémentées (Sprint 1) :
 *   BROUILLON         + SOUMETTRE → SOUMISE
 *   EN_VALIDATION_ETAPE + REJETER  → REJETEE
 *   EN_VALIDATION_ETAPE + VALIDER  → EN_VALIDATION_ETAPE (position+1)
 *                                    ou EN_INSTRUCTION_ANALYSTE_RH (dernière étape)
 */
@Component
@RequiredArgsConstructor
public class AbsenceStateMachine {

    private final EtapeDemandeSnapshotRepository etapeDemandeSnapshotRepository;
    private final DGConditionnelService dgConditionnelService;

    /** EX-8 — Un rejet système n'est légal que depuis un état non final. */
    private static final Set<StatutDemande> ETATS_REJETABLES_SYSTEME = Set.of(
            StatutDemande.BROUILLON,
            StatutDemande.SOUMISE,
            StatutDemande.EN_VALIDATION_ETAPE,
            StatutDemande.EN_INSTRUCTION_ANALYSTE_RH,
            StatutDemande.EN_VALIDATION_DRH);

    /**
     * Applique {@code event} sur {@code demande} en mutant son statut (et éventuellement
     * sa {@code positionEtapeCourante}). La persistance reste à la charge de l'appelant.
     *
     * @throws TransitionIllegaleException si l'événement est illégal depuis le statut courant
     */
    public void sendEvent(DemandeAbsence demande, AbsenceEvent event) {
        StatutDemande nouveau = calculerTransition(demande, event);
        if (nouveau == null) {
            throw new TransitionIllegaleException(
                    "Transition " + event + " illegale depuis " + demande.getStatut());
        }
        demande.setStatut(nouveau);
    }

    // ── Logique de transition ────────────────────────────────────────────────

    private StatutDemande calculerTransition(DemandeAbsence demande, AbsenceEvent event) {
        if (event == AbsenceEvent.REJETER_SYSTEME) {
            // EX-8 — borné aux états non finaux (pas de rejet système d'une demande
            // déjà VALIDEE / CLOTUREE / REJETEE, qui produirait une incohérence de solde).
            return ETATS_REJETABLES_SYSTEME.contains(demande.getStatut())
                    ? StatutDemande.REJETEE_PAR_LE_SYSTEME
                    : null;
        }

        return switch (demande.getStatut()) {
            case BROUILLON ->
                    event == AbsenceEvent.SOUMETTRE ? StatutDemande.SOUMISE : null;
            case EN_VALIDATION_ETAPE ->
                    calculerApresDecisionEtape(demande, event);
            case EN_INSTRUCTION_ANALYSTE_RH ->
                    event == AbsenceEvent.TRANSMETTRE ? StatutDemande.EN_VALIDATION_DRH : null;
            case SOUMISE ->
                    null; // Attend une décision asynchrone si nécessaire, ou on gère le REJETER_SYSTEME
            case EN_VALIDATION_DRH -> switch (event) {
                case REJETER -> StatutDemande.REJETEE;
                case VALIDER -> StatutDemande.VALIDEE;
                default -> null;
            };
            default -> null;
        };
    }

    private StatutDemande calculerApresDecisionEtape(DemandeAbsence demande,
                                                      AbsenceEvent event) {
        if (event == AbsenceEvent.REJETER) return StatutDemande.REJETEE;

        if (event != AbsenceEvent.VALIDER) return null;

        List<EtapeDemandeSnapshot> intermediaires =
                etapeDemandeSnapshotRepository.findIntermediairesOrdonnees(demande.getId());

        int dernierePosition = intermediaires.size() - 1;
        int positionCourante  = demande.getPositionEtapeCourante() != null
                ? demande.getPositionEtapeCourante() : 0;

        if (positionCourante < dernierePosition) {
            demande.setPositionEtapeCourante(positionCourante + 1);
            return StatutDemande.EN_VALIDATION_ETAPE;
        }

        // Toutes les étapes intermédiaires franchies → injection DG conditionnelle ou instruction Analyste RH
        if (dgConditionnelService.necessiteInjection(demande)) {
            dgConditionnelService.injecterEtapeConditionnelle(demande);
            return StatutDemande.EN_VALIDATION_ETAPE;
        }
        return StatutDemande.EN_INSTRUCTION_ANALYSTE_RH;
    }
}
