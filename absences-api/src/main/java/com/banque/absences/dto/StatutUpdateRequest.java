package com.banque.absences.dto;

import com.banque.absences.domain.StatutDemande;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatutUpdateRequest {

    @NotNull(message = "Le statut est obligatoire")
    private StatutDemande statut;

    private String commentaire;
}
