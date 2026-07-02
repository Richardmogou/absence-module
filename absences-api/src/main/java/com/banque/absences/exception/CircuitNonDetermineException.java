package com.banque.absences.exception;

public class CircuitNonDetermineException extends RuntimeException {

    public CircuitNonDetermineException() {
        super("Le grade porte par le jeton ne correspond a aucune regle d'affectation");
    }

    public CircuitNonDetermineException(String message) {
        super(message);
    }
}
