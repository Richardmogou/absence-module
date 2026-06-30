package com.banque.absences.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RetourAnticipeRequest(@NotNull LocalDate dateRetourEffective) {}
