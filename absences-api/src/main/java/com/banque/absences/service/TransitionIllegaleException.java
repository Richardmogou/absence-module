package com.banque.absences.service;

/**
 * Levée quand un événement ne correspond à aucune transition valide
 * depuis le statut courant de la demande.
 */
public class TransitionIllegaleException extends RuntimeException {

    public TransitionIllegaleException(String message) {
        super(message);
    }
}
