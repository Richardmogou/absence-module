package com.banque.absences.statemachine;

import com.banque.absences.domain.*;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.service.AbsenceStateMachine;
import com.banque.absences.service.DGConditionnelService;
import com.banque.absences.service.TransitionIllegaleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de {@link AbsenceStateMachine}.
 *
 * Circuit Agent : 3 étapes intermédiaires (Back-up / Manager / Chef de processus).
 * Aucun contexte Spring, aucune BDD.
 */
class AbsenceStateMachineTest {

    private EtapeDemandeSnapshotRepository snapshotRepo;
    private DGConditionnelService          dgConditionnelService;
    private AbsenceStateMachine            machine;

    @BeforeEach
    void setUp() {
        snapshotRepo             = mock(EtapeDemandeSnapshotRepository.class);
        dgConditionnelService    = mock(DGConditionnelService.class);
        machine                  = new AbsenceStateMachine(snapshotRepo, dgConditionnelService);
    }

    // ── BROUILLON → SOUMISE ───────────────────────────────────────────────────

    @Test
    @DisplayName("BROUILLON + SOUMETTRE → SOUMISE")
    void brouillon_soumettre_soumise() {
        DemandeAbsence demande = demandeAvecStatut(StatutDemande.BROUILLON);

        machine.sendEvent(demande, AbsenceEvent.SOUMETTRE);

        assertThat(demande.getStatut()).isEqualTo(StatutDemande.SOUMISE);
    }

    @Test
    @DisplayName("BROUILLON + VALIDER → TransitionIllegaleException")
    void brouillon_valider_illegal() {
        DemandeAbsence demande = demandeAvecStatut(StatutDemande.BROUILLON);

        assertThatThrownBy(() -> machine.sendEvent(demande, AbsenceEvent.VALIDER))
                .isInstanceOf(TransitionIllegaleException.class)
                .hasMessageContaining("VALIDER")
                .hasMessageContaining("BROUILLON");
    }

    // ── EN_VALIDATION_ETAPE : 3 appels VALIDER successifs ────────────────────

    @Test
    @DisplayName("Circuit Agent — 3 VALIDER successifs : position 0→1→2 puis EN_INSTRUCTION_ANALYSTE_RH")
    void circuitAgent_troisValidations_analysteRh() {
        UUID demandeId = UUID.randomUUID();
        DemandeAbsence demande = demandeEnValidation(demandeId, 0);

        // 3 étapes intermédiaires : Back-up, Manager, Chef de processus
        List<EtapeDemandeSnapshot> intermediaires = List.of(
                etape(demandeId, 1, "Back-up hiérarchique"),
                etape(demandeId, 2, "Manager Direct"),
                etape(demandeId, 3, "Chef de Processus")
        );
        when(snapshotRepo.findIntermediairesOrdonnees(demandeId))
                .thenReturn(intermediaires);
        when(dgConditionnelService.necessiteInjection(demande))
                .thenReturn(false);

        // 1er VALIDER : position 0 → 1, statut reste EN_VALIDATION_ETAPE
        machine.sendEvent(demande, AbsenceEvent.VALIDER);
        assertThat(demande.getStatut()).isEqualTo(StatutDemande.EN_VALIDATION_ETAPE);
        assertThat(demande.getPositionEtapeCourante()).isEqualTo(1);

        // 2e VALIDER : position 1 → 2, statut reste EN_VALIDATION_ETAPE
        machine.sendEvent(demande, AbsenceEvent.VALIDER);
        assertThat(demande.getStatut()).isEqualTo(StatutDemande.EN_VALIDATION_ETAPE);
        assertThat(demande.getPositionEtapeCourante()).isEqualTo(2);

        // 3e VALIDER : position 2 = dernière → EN_INSTRUCTION_ANALYSTE_RH
        machine.sendEvent(demande, AbsenceEvent.VALIDER);
        assertThat(demande.getStatut()).isEqualTo(StatutDemande.EN_INSTRUCTION_ANALYSTE_RH);

        // Le repository a été appelé 3 fois (une par VALIDER)
        verify(snapshotRepo, times(3)).findIntermediairesOrdonnees(demandeId);
        verify(dgConditionnelService).necessiteInjection(demande);
        verify(dgConditionnelService, never()).injecterEtapeConditionnelle(any());
    }

    @Test
    @DisplayName("Circuit Agent Mission longue — dernière étape intermédiaire validée -> injection DG")
    void circuitAgentMissionLongue_derniereValidation_injecteDg() {
        UUID demandeId = UUID.randomUUID();
        DemandeAbsence demande = demandeMissionLongueAgentEnValidation(demandeId, 2);

        List<EtapeDemandeSnapshot> intermediaires = List.of(
                etape(demandeId, 0, "Back-up hiérarchique"),
                etape(demandeId, 1, "Manager Direct"),
                etape(demandeId, 2, "Chef de Processus")
        );
        when(snapshotRepo.findIntermediairesOrdonnees(demandeId))
                .thenReturn(intermediaires);
        when(dgConditionnelService.necessiteInjection(demande))
                .thenReturn(true);

        machine.sendEvent(demande, AbsenceEvent.VALIDER);

        assertThat(demande.getStatut()).isEqualTo(StatutDemande.EN_VALIDATION_ETAPE);
        verify(dgConditionnelService).necessiteInjection(demande);
        verify(dgConditionnelService).injecterEtapeConditionnelle(demande);
    }

    // ── EN_VALIDATION_ETAPE + REJETER ─────────────────────────────────────────

    @Test
    @DisplayName("EN_VALIDATION_ETAPE + REJETER → REJETEE")
    void enValidation_rejeter_rejetee() {
        UUID demandeId = UUID.randomUUID();
        DemandeAbsence demande = demandeEnValidation(demandeId, 0);

        machine.sendEvent(demande, AbsenceEvent.REJETER);

        assertThat(demande.getStatut()).isEqualTo(StatutDemande.REJETEE);
        verifyNoInteractions(snapshotRepo); // REJETER ne consulte pas le snapshot
    }

    // ── Transition illégale depuis statut terminal ────────────────────────────

    @Test
    @DisplayName("REJETEE + SOUMETTRE → TransitionIllegaleException")
    void rejetee_soumettre_illegal() {
        DemandeAbsence demande = demandeAvecStatut(StatutDemande.REJETEE);

        assertThatThrownBy(() -> machine.sendEvent(demande, AbsenceEvent.SOUMETTRE))
                .isInstanceOf(TransitionIllegaleException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static DemandeAbsence demandeAvecStatut(StatutDemande statut) {
        DemandeAbsence d = new DemandeAbsence();
        d.setStatut(statut);
        return d;
    }

    private static DemandeAbsence demandeEnValidation(UUID id, int position) {
        DemandeAbsence d = new DemandeCongeAnnuel();
        initialiserDemandeEnValidation(id, position, d);
        return d;
    }

    private static DemandeAbsence demandeMissionLongueAgentEnValidation(UUID id, int position) {
        DemandeAbsence d = new DemandeMissionLongue();
        initialiserDemandeEnValidation(id, position, d);
        d.setCircuitNom("AGENT");
        d.setType(TypeAbsence.MISSION_LONGUE);
        return d;
    }

    private static void initialiserDemandeEnValidation(UUID id, int position, DemandeAbsence d) {
        // Injection manuelle de l'id via réflexion (champ final UUID)
        try {
            var field = DemandeAbsence.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(d, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        d.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
        d.setPositionEtapeCourante(position);
    }

    private static EtapeDemandeSnapshot etape(UUID demandeId, int ordre, String libelle) {
        EtapeDemandeSnapshot e = new EtapeDemandeSnapshot();
        e.setDemandeId(demandeId);
        e.setOrdre(ordre);
        e.setLibelle(libelle);
        e.setStatut(EtapeDemandeSnapshot.StatutEtape.EN_ATTENTE);
        return e;
    }
}
