package com.banque.absences.service;

public class ModificationImpossibleException extends RuntimeException {
    public ModificationImpossibleException(String message) {
        super(message);
    }
}
