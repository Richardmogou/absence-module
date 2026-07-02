package com.banque.absences.service;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoublonDetectionServiceTest {

    private DemandeAbsenceRepository demandeAbsenceRepository;
    private DoublonDetectionService service;

    @BeforeEach
    void setUp() {
        demandeAbsenceRepository = mock(DemandeAbsenceRepository.class);
        service = new DoublonDetectionService(demandeAbsenceRepository);
    }

    @Test
    @DisplayName("US-CIR-001 - Une demande jointive a J+1 est detectee comme doublon")
    void detecterDoublonDemandeJointive() {
        DemandeAbsence existante = demande(
                "agent-1",
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 14));
        DemandeAbsence nouvelle = demande(
                "agent-1",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 20));

        when(demandeAbsenceRepository.findByDemandeurIdentifiantExterneAndType(
                "agent-1", TypeAbsence.CONGE_ANNUEL))
                .thenReturn(List.of(existante));

        assertThat(service.detecterDoublon(nouvelle)).isTrue();
    }

    @Test
    @DisplayName("US-CIR-001 - Une demande separee de plus d'un jour n'est pas un doublon")
    void neDetectePasDoublonDemandeSeparee() {
        DemandeAbsence existante = demande(
                "agent-1",
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 14));
        DemandeAbsence nouvelle = demande(
                "agent-1",
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 20));

        when(demandeAbsenceRepository.findByDemandeurIdentifiantExterneAndType(
                "agent-1", TypeAbsence.CONGE_ANNUEL))
                .thenReturn(List.of(existante));

        assertThat(service.detecterDoublon(nouvelle)).isFalse();
    }

    private static DemandeAbsence demande(String demandeurId, LocalDate dateDebut, LocalDate dateFin) {
        DemandeAbsence demande = new DemandeAbsence();
        demande.setId(UUID.randomUUID());
        demande.setDemandeurIdentifiantExterne(demandeurId);
        demande.setType(TypeAbsence.CONGE_ANNUEL);
        demande.setStatut(StatutDemande.BROUILLON);
        demande.setDateDebut(dateDebut);
        demande.setDateFin(dateFin);
        return demande;
    }
}
