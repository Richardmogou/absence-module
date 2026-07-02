package com.banque.absences.dto;

import com.banque.absences.domain.TypeAbsence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AbsenceRequest {

    @NotBlank(message = "L'identifiant du demandeur est obligatoire")
    private String demandeurIdentifiantExterne;

    @NotBlank(message = "L'identifiant de l'unité est obligatoire")
    private String uniteIdentifiantExterne;

    @NotNull(message = "Le type d'absence est obligatoire")
    private TypeAbsence typeAbsence;

    @NotNull(message = "La date de début est obligatoire")
    private LocalDate dateDebut;

    @NotNull(message = "La date de fin est obligatoire")
    private LocalDate dateFin;

    private Integer nombreJours;
}
