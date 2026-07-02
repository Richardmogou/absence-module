package com.banque.absences.service;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.DemandeMissionLongue;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DGConditionnelServiceTest {

    private EtapeDemandeSnapshotRepository repository;
    private DGConditionnelService service;

    @BeforeEach
    void setUp() {
        repository = mock(EtapeDemandeSnapshotRepository.class);
        service = new DGConditionnelService(repository);
    }

    @Test
    @DisplayName("Mission longue Circuit Agent non injectée -> nécessite injection")
    void missionLongueAgentNonInjectee_necessiteInjection() {
        UUID demandeId = UUID.randomUUID();
        DemandeAbsence demande = demandeMissionLongueAgent(demandeId, 2);
        when(repository.findByDemandeIdAndMecanisme(demandeId, MecanismeResolution.DG_CONDITIONNEL))
                .thenReturn(Optional.empty());

        assertThat(service.necessiteInjection(demande)).isTrue();
    }

    @Test
    @DisplayName("Injection DG conditionnelle -> un snapshot à la position suivante")
    void injecterEtapeConditionnelle_creeSnapshotPositionSuivante() {
        UUID demandeId = UUID.randomUUID();
        DemandeAbsence demande = demandeMissionLongueAgent(demandeId, 2);
        ArgumentCaptor<EtapeDemandeSnapshot> captor =
                ArgumentCaptor.forClass(EtapeDemandeSnapshot.class);

        service.injecterEtapeConditionnelle(demande);

        verify(repository, times(1)).save(captor.capture());
        EtapeDemandeSnapshot snap = captor.getValue();
        assertThat(snap.getDemandeId()).isEqualTo(demandeId);
        assertThat(snap.getOrdre()).isEqualTo(3);
        assertThat(snap.getPosition()).isEqualTo(3);
        assertThat(snap.getMecanismeResolution()).isEqualTo(MecanismeResolution.DG_CONDITIONNEL);
        assertThat(snap.getLibelle()).isEqualTo("DG");
        assertThat(snap.isVerrouille()).isFalse();
        assertThat(demande.getPositionEtapeCourante()).isEqualTo(3);
    }

    @Test
    @DisplayName("DG déjà injecté -> pas de nouvelle injection requise")
    void dgDejaInjecte_pasInjection() {
        UUID demandeId = UUID.randomUUID();
        DemandeAbsence demande = demandeMissionLongueAgent(demandeId, 2);
        when(repository.findByDemandeIdAndMecanisme(demandeId, MecanismeResolution.DG_CONDITIONNEL))
                .thenReturn(Optional.of(new EtapeDemandeSnapshot()));

        assertThat(service.necessiteInjection(demande)).isFalse();
    }

    private static DemandeAbsence demandeMissionLongueAgent(UUID id, int position) {
        DemandeAbsence d = new DemandeMissionLongue();
        try {
            var field = DemandeAbsence.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(d, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        d.setCircuitNom("AGENT");
        d.setType(TypeAbsence.MISSION_LONGUE);
        d.setPositionEtapeCourante(position);
        return d;
    }
}
