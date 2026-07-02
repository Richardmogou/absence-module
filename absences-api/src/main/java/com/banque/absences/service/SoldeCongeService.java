package com.banque.absences.service;

import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.SoldeCongeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SoldeCongeService {

    private final SoldeCongeRepository soldeCongeRepository;

    @Transactional
    public void recrediter(String employeId, TypeAbsence type, int jours) {
        if (jours <= 0) return;
        int exercice = LocalDate.now().getYear();
        soldeCongeRepository.recrediterJours(employeId, jours, exercice);
    }

    @Transactional
    public void debiter(String employeId, TypeAbsence type, int jours) {
        if (jours <= 0) return;
        int exercice = LocalDate.now().getYear();
        soldeCongeRepository.debiterJours(employeId, jours, exercice);
    }
}
