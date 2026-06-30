package com.banque.absences.dto;

import com.banque.absences.domain.MecanismeResolution;

public record EtapeIntermediaireDto(
        MecanismeResolution mecanismeResolution,
        String roleHabilite,
        Integer profondeurHierarchique) {}
