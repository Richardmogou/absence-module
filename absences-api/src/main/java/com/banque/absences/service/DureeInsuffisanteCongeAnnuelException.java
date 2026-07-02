package com.banque.absences.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DureeInsuffisanteCongeAnnuelException extends RuntimeException {
    public DureeInsuffisanteCongeAnnuelException(String message) {
        super(message);
    }
}
