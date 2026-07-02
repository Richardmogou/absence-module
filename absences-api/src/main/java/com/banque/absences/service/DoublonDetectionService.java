package com.banque.absences.service;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.repository.DemandeAbsenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoublonDetectionService {

    private static final Set<StatutDemande> STATUTS_IGNORES = Set.of(
            StatutDemande.ANNULEE,
            StatutDemande.REJETEE);

    private final DemandeAbsenceRepository demandeAbsenceRepository;

    public boolean detecterDoublon(DemandeAbsence demande) {
        List<DemandeAbsence> existantes = demandeAbsenceRepository
                .findByDemandeurIdentifiantExterne(demande.getDemandeurIdentifiantExterne());
                
        return existantes.stream()
                .filter(d -> !Objects.equals(d.getId(), demande.getId()))
                .filter(d -> !STATUTS_IGNORES.contains(d.getStatut()))
                .anyMatch(d -> periodeSeChevauche(d, demande, 1));
    }

    private boolean periodeSeChevauche(DemandeAbsence a, DemandeAbsence b, int margeJours) {
        LocalDate debutA = a.getDateDebut();
        LocalDate finA = a.getDateFin() != null ? a.getDateFin() : debutA;
        
        LocalDate debutB = b.getDateDebut();
        LocalDate finB = b.getDateFin() != null ? b.getDateFin() : debutB;

        if (debutA == null || debutB == null) {
            return false;
        }

        debutA = debutA.minusDays(margeJours);
        finA = finA.plusDays(margeJours);

        return !(finB.isBefore(debutA) || debutB.isAfter(finA));
    }
}
