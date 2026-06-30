package com.banque.absences.service;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.repository.RegleAffectationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CircuitDeterminationService {

    private final ClaimReaderService         claimReaderService;
    private final RegleAffectationRepository regleAffectationRepository;

    /**
     * Retourne le circuit applicable à la demande en cherchant d'abord
     * par grade + type d'absence, puis par grade uniquement en fallback.
     */
    @Cacheable(value = "reglesAffectation", key = "#demande.type.name() + '-' + @claimReaderService.lireClaimGrade()")
    public Optional<ModeleCircuit> determinerCircuitApplicable(DemandeAbsence demande) {
        String grade = claimReaderService.lireClaimGrade();

        // Priorité 1 : circuit spécifique au grade + type d'absence
        Optional<ModeleCircuit> circuit = regleAffectationRepository
                .findFirstByGradeDeclencheurAndEtapeModeleCircuitModeleCircuitTypeAbsenceCibleOrderByPrioriteAsc(
                        grade, demande.getType())
                .map(regle -> regle.getCircuit());

        if (circuit.isPresent()) return circuit;

        // Fallback : circuit par grade uniquement (comportement historique)
        return regleAffectationRepository
                .findFirstByGradeDeclencheurOrderByPrioriteAsc(grade)
                .map(regle -> regle.getCircuit());
    }
}
