package com.banque.absences.dto;

import jakarta.validation.constraints.NotNull;

/**
 * US-CIR-004 / US-AGT-004 — Payload de décision à une étape du circuit.
 */
public record ValidationEtapeRequest(

        @NotNull(message = "La décision est obligatoire")
        Decision decision,

        String motif,

        Integer nombreJoursAjuste
) {
    public enum Decision { VALIDER, REJETER }
}
