package com.banque.absences.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    /**
     * Deux validateurs qui traitent la même demande au même instant est un scénario
     * nominal en production : le perdant doit recevoir un 409 exploitable par le front,
     * pas un 500 brut (comportement observé au test de charge du 2026-07-17).
     */
    @Test
    @DisplayName("Verrouillage optimiste : 409 CONFLIT_TRAITEMENT_SIMULTANE, pas un 500")
    void verrouillageOptimiste_repondConflitExploitable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<GlobalExceptionHandler.ApiError> reponse =
                handler.handleVerrouillageOptimiste(
                        new ObjectOptimisticLockingFailureException("DemandeAbsence", "un-id"));

        assertThat(reponse.getStatusCode().value()).isEqualTo(409);
        assertThat(reponse.getBody().code()).isEqualTo("CONFLIT_TRAITEMENT_SIMULTANE");
        assertThat(reponse.getBody().message()).contains("rechargez");
    }
}
