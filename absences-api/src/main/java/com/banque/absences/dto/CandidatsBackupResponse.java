package com.banque.absences.dto;

import java.util.List;

public record CandidatsBackupResponse(
        List<String> pairsIds,
        String managerDirectId) {}
