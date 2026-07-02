package com.banque.absences.service;

import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.dto.DoublonDetecteResult;
import com.banque.absences.exception.EmployeTypeIntrouvableException;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * US-ADM-003 — Contrôle de cohérence anti-doublon d'un modèle de circuit.
 *
 * Détecte le cas où une étape HIERARCHIQUE résoudrait vers un grade
 * déjà couvert par une étape ROLE_FIXE_* du même circuit.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CircuitCoherenceCheckerService {

    private final EtapeModeleCircuitRepository etapeModeleCircuitRepository;
    private final HierarchicalChainResolver hierarchicalChainResolver;

    public Optional<DoublonDetecteResult> verifierCoherence(
            UUID circuitId, String gradeDeclencheur) {

        List<EtapeModeleCircuit> etapes =
                etapeModeleCircuitRepository.findByModeleCircuitIdOrderByOrdreAsc(circuitId);

        String employeType = hierarchicalChainResolver
                .resoudreEmployeTypeParGrade(gradeDeclencheur)
                .orElseThrow(() -> new EmployeTypeIntrouvableException(gradeDeclencheur));

        for (EtapeModeleCircuit etapeH : etapes) {
            Optional<RegleAffectation> regleHOpt = etapeH.getRegles().stream()
                    .filter(r -> r.getMecanisme() == MecanismeResolution.HIERARCHIQUE)
                    .findFirst();
            if (regleHOpt.isEmpty()) continue;

            RegleAffectation regleH = regleHOpt.get();
            int profondeur = regleH.getProfondeurHierarchique() != null
                    ? regleH.getProfondeurHierarchique()
                    : etapeH.getOrdre() + 1;

            Optional<String> resoluId =
                    hierarchicalChainResolver.resoudreHierarchique(employeType, profondeur);
            if (resoluId.isEmpty()) continue;

            String gradeResolu = hierarchicalChainResolver
                    .resoudreGradeParIdentifiant(resoluId.get())
                    .orElse(null);
            if (gradeResolu == null) continue;

            for (EtapeModeleCircuit etapeRF : etapes) {
                boolean estRoleFixe = etapeRF.getRegles().stream()
                        .anyMatch(r -> r.getMecanisme() != MecanismeResolution.HIERARCHIQUE);
                if (!estRoleFixe) continue;

                boolean doublon = etapeRF.getRegles().stream()
                        .filter(r -> r.getMecanisme() != MecanismeResolution.HIERARCHIQUE)
                        .anyMatch(r -> gradeResolu.equals(r.getRoleKeycloakCible()));
                if (doublon) {
                    return Optional.of(
                            new DoublonDetecteResult(etapeH.getId(), etapeRF.getId()));
                }
            }
        }
        return Optional.empty();
    }
}
