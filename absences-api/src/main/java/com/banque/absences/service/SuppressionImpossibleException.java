package com.banque.absences.service;

public class SuppressionImpossibleException extends RuntimeException {
    public SuppressionImpossibleException(String message) {
        super(message);
    }
}
