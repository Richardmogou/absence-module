package com.banque.absences.service;

/** Cible de forçage refusée sur PATCH /{id}/statut (CDCT EX-9, §4.1.7-E). */
public class OverrideCibleInterditeException extends RuntimeException {
    public OverrideCibleInterditeException(String message) {
        super(message);
    }
}
