package com.banque.absences.dto;

import java.util.UUID;

public record DoublonDetecteResult(UUID etapeHierarchiqueId, UUID etapeRoleFixeRedondanteId) {}
