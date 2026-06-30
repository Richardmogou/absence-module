package com.banque.absences.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ModificationDemandeRequest(
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        @Min(1) Integer nombreJours) {}
