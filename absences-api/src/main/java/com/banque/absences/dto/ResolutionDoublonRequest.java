package com.banque.absences.dto;

import com.banque.absences.domain.ChoixResolution;

import java.util.UUID;

public record ResolutionDoublonRequest(
        ChoixResolution choix,
        UUID etapeId,
        String gradeDeclencheur) {}
