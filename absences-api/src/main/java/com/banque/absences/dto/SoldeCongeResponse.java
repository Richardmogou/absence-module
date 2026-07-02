package com.banque.absences.dto;

public record SoldeCongeResponse(
        String employeIdentifiantExterne,
        int exercice,
        int joursAcquis,
        int joursPris,
        int joursRestants
) {}
