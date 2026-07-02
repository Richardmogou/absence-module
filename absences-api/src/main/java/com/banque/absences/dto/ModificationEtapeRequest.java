package com.banque.absences.dto;

import com.banque.absences.domain.MecanismeResolution;

public record ModificationEtapeRequest(MecanismeResolution mecanismeResolution, String roleHabilite) {}
