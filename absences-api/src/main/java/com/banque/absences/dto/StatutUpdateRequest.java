package com.banque.absences.dto;

import com.banque.absences.domain.StatutDemande;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StatutUpdateRequest {

    @NotNull(message = "Le statut est obligatoire")
    private StatutDemande statut;

    /**
     * Motif du forçage — obligatoire (CDCT EX-9, §4.1.7-E). Anciennement {@code commentaire}
     * et facultatif : un override sans justification est intraçable a posteriori.
     */
    @NotBlank(message = "Le motif est obligatoire pour un forçage de statut")
    @Size(max = 500, message = "Le motif ne peut excéder 500 caractères")
    private String motif;
}
