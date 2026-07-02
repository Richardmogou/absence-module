package com.banque.absences.exception;

import com.banque.absences.dto.DoublonDetecteResult;

import java.util.UUID;

public class DoublonValidateurDetecteException extends RuntimeException {

    private final UUID circuitId;
    private final UUID etapeHierarchiqueId;
    private final UUID etapeRoleFixeRedondanteId;

    public DoublonValidateurDetecteException(UUID circuitId, DoublonDetecteResult doublon) {
        super("Doublon validateur detecte dans le circuit " + circuitId);
        this.circuitId                = circuitId;
        this.etapeHierarchiqueId      = doublon.etapeHierarchiqueId();
        this.etapeRoleFixeRedondanteId = doublon.etapeRoleFixeRedondanteId();
    }

    public UUID getCircuitId()                 { return circuitId; }
    public UUID getEtapeHierarchiqueId()       { return etapeHierarchiqueId; }
    public UUID getEtapeRoleFixeRedondanteId() { return etapeRoleFixeRedondanteId; }
}
