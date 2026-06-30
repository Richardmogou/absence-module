package com.banque.absences.service;

import com.banque.absences.domain.ChoixResolution;
import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.dto.CreationCircuitRequest;
import com.banque.absences.dto.DoublonDetecteResult;
import com.banque.absences.dto.EtapeIntermediaireDto;
import com.banque.absences.dto.ModificationEtapeRequest;
import com.banque.absences.exception.DoublonValidateurDetecteException;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import com.banque.absences.repository.ModeleCircuitRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircuitAdminService {

    private final ModeleCircuitRepository        modeleCircuitRepository;
    private final EtapeModeleCircuitRepository   etapeModeleCircuitRepository;
    private final CircuitCoherenceCheckerService circuitCoherenceCheckerService;

    @Transactional(noRollbackFor = DoublonValidateurDetecteException.class)
    @PreAuthorize("hasRole('ADMIN_RH')")
    public ModeleCircuit creerCircuit(CreationCircuitRequest dto) {
        for (EtapeIntermediaireDto e : dto.etapesIntermediaires()) {
            if (e.mecanismeResolution() == MecanismeResolution.DG_CONDITIONNEL) {
                throw new EtapeInvalideException(
                        "DG_CONDITIONNEL ne peut jamais etre compose explicitement");
            }
        }

        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom(dto.nom());
        circuit.setTypeAbsenceCible(dto.typeAbsenceCible());
        circuit.setUniteIdentifianteExterne(dto.uniteIdentifianteExterne());
        circuit.setEstModeleNomme(false);
        modeleCircuitRepository.save(circuit);

        int position = 0;
        for (EtapeIntermediaireDto e : dto.etapesIntermediaires()) {
            sauvegarderEtape(circuit, position++, e.mecanismeResolution(), e.roleHabilite(),
                    false, e.profondeurHierarchique());
        }
        sauvegarderEtape(circuit, position++, MecanismeResolution.ROLE_FIXE_GLOBAL, "ANALYSTE_RH", true, null);
        sauvegarderEtape(circuit, position,   MecanismeResolution.ROLE_FIXE_GLOBAL, "DRH",         true, null);

        // US-ADM-003 / US-ADM-004 / US-ADM-006 : contrôle de cohérence anti-doublon.
        // Lève EmployeTypeIntrouvableException (422) si aucun employé-type n'existe pour le grade,
        // ou DoublonValidateurDetecteException (409) si un doublon est détecté.
        if (dto.gradeDeclencheur() != null) {
            Optional<DoublonDetecteResult> doublon =
                    circuitCoherenceCheckerService.verifierCoherence(
                            circuit.getId(), dto.gradeDeclencheur());
            if (doublon.isPresent()) {
                throw new DoublonValidateurDetecteException(circuit.getId(), doublon.get());
            }
        }

        circuit.setEstModeleNomme(true);
        return modeleCircuitRepository.save(circuit);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN_RH')")
    public ModeleCircuit resoudreDoublon(UUID circuitId, ChoixResolution choix,
                                         UUID etapeRoleFixeRedondanteId,
                                         String gradeDeclencheur) {
        if (choix == ChoixResolution.SUPPRIMER) {
            etapeModeleCircuitRepository.deleteById(etapeRoleFixeRedondanteId);
            // Flush nécessaire pour que le checker ne voie plus l'étape supprimée
            etapeModeleCircuitRepository.flush();

            Optional<DoublonDetecteResult> reverification =
                    circuitCoherenceCheckerService.verifierCoherence(circuitId, gradeDeclencheur);
            if (reverification.isPresent()) {
                throw new DoublonValidateurDetecteException(circuitId, reverification.get());
            }
        }

        ModeleCircuit circuit = modeleCircuitRepository.findById(circuitId)
                .orElseThrow(() -> new EntityNotFoundException("Circuit introuvable : " + circuitId));
        circuit.setEstModeleNomme(true);
        return modeleCircuitRepository.save(circuit);
    }

    public List<ModeleCircuit> listerCircuits() {
        return modeleCircuitRepository.findAllByActifTrue();
    }

    public ModeleCircuit findById(UUID id) {
        return modeleCircuitRepository.findByIdWithEtapes(id)
                .orElseThrow(() -> new EntityNotFoundException("Circuit introuvable : " + id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN_RH')")
    public void supprimerCircuit(UUID id) {
        ModeleCircuit circuit = modeleCircuitRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Circuit introuvable : " + id));
        circuit.setActif(false);
        modeleCircuitRepository.save(circuit);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN_RH')")
    public void modifierEtape(UUID etapeId, ModificationEtapeRequest dto) {
        EtapeModeleCircuit etape = etapeModeleCircuitRepository.findById(etapeId)
                .orElseThrow(() -> new EntityNotFoundException("Etape introuvable : " + etapeId));
        if (etape.isEstVerrouillable()) {
            throw new EtapeVerrouilleeException(
                    "Cette etape (" + etape.getLibelle() + ") est verrouillee");
        }
        etape.setLibelle(dto.roleHabilite());
        if (!etape.getRegles().isEmpty()) {
            RegleAffectation regle = etape.getRegles().get(0);
            regle.setMecanisme(dto.mecanismeResolution());
            regle.setRoleKeycloakCible(dto.roleHabilite());
        }
        etapeModeleCircuitRepository.save(etape);
    }

    private void sauvegarderEtape(ModeleCircuit circuit, int position,
                                   MecanismeResolution mecanisme, String roleHabilite,
                                   boolean verrouillee, Integer profondeurHierarchique) {
        EtapeModeleCircuit etape = new EtapeModeleCircuit();
        etape.setModeleCircuit(circuit);
        etape.setOrdre(position);
        etape.setLibelle(roleHabilite != null ? roleHabilite : mecanisme.name());
        etape.setEstVerrouillable(verrouillee);

        RegleAffectation regle = new RegleAffectation();
        regle.setEtapeModeleCircuit(etape);
        regle.setMecanisme(mecanisme);
        regle.setRoleKeycloakCible(roleHabilite);
        regle.setProfondeurHierarchique(profondeurHierarchique);
        regle.setPriorite(1);

        etape.getRegles().add(regle);
        etapeModeleCircuitRepository.save(etape);
    }
}
