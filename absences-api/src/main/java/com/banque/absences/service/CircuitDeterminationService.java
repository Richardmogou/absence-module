package com.banque.absences.service;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.repository.RegleAffectationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CircuitDeterminationService {

    private final ClaimReaderService         claimReaderService;
    private final RegleAffectationRepository regleAffectationRepository;

    @Cacheable(
        value = "reglesAffectation",
        key   = "#demande.type.name() + '-' + @claimReaderService.lireClaimGrade() + '-' + @claimReaderService.lireClaimReseau().orElse('ALL')"
    )
    public Optional<ModeleCircuit> determinerCircuitApplicable(DemandeAbsence demande) {
        String grade = claimReaderService.lireClaimGrade();
        String unite = claimReaderService.lireClaimReseau().orElse(null);

        // Priorité 1 : circuit spécifique à l'unité
        if (unite != null) {
            Optional<ModeleCircuit> circuit = premier(
                    regleAffectationRepository.findByGradeAndTypeAndUnite(grade, demande.getType(), unite));
            if (circuit.isPresent()) return circuit;
        }

        // Priorité 2 : circuit global pour ce grade + type d'absence
        Optional<ModeleCircuit> circuit = premier(
                regleAffectationRepository.findByGradeAndTypeGlobal(grade, demande.getType()));
        if (circuit.isPresent()) return circuit;

        // Fallback : circuit global pour ce grade uniquement
        return premier(regleAffectationRepository.findByGradeGlobal(grade));
    }

    private Optional<ModeleCircuit> premier(List<RegleAffectation> regles) {
        return regles.isEmpty() ? Optional.empty() : Optional.of(regles.get(0).getCircuit());
    }
}
