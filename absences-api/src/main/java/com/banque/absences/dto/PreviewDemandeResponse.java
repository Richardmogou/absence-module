package com.banque.absences.dto;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.ModeleCircuit;

import java.util.List;

public record PreviewDemandeResponse(
        DemandeAbsence demande,
        ModeleCircuit circuitDetermine,
        boolean doublonDetecte,
        List<DemandeAbsence> demandesSimilaires
) {
}
