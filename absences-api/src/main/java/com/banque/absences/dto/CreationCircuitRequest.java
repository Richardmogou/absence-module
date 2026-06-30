package com.banque.absences.dto;

import com.banque.absences.domain.TypeAbsence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreationCircuitRequest(
        @NotBlank(message = "Le nom du circuit est obligatoire")
        String nom,

        @NotNull(message = "Le type d'absence cible est obligatoire")
        TypeAbsence typeAbsenceCible,

        String gradeDeclencheur,

        String uniteIdentifianteExterne,

        List<EtapeIntermediaireDto> etapesIntermediaires
) {}
