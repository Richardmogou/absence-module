package com.banque.absences.controller;

import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.dto.CreationCircuitRequest;
import com.banque.absences.dto.ModificationEtapeRequest;
import com.banque.absences.dto.ResolutionDoublonRequest;
import com.banque.absences.service.CircuitAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v5/admin/circuits")
@RequiredArgsConstructor
public class CircuitAdminController {

    private final CircuitAdminService circuitAdminService;

    @GetMapping
    public ResponseEntity<List<ModeleCircuit>> lister() {
        return ResponseEntity.ok(circuitAdminService.listerCircuits());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModeleCircuit> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(circuitAdminService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ModeleCircuit> creer(@RequestBody @Valid CreationCircuitRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(circuitAdminService.creerCircuit(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        circuitAdminService.supprimerCircuit(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-actif")
    public ResponseEntity<Void> toggleActif(@PathVariable UUID id) {
        circuitAdminService.toggleActif(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/etapes")
    public ResponseEntity<Void> modifierEtape(@PathVariable UUID id,
                                               @RequestBody ModificationEtapeRequest dto) {
        circuitAdminService.modifierEtape(id, dto);
        return ResponseEntity.noContent().build();
    }

    /**
     * US-ADM-005 — Résolution du doublon validateur détecté lors de la création.
     * SUPPRIMER : supprime l'étape ROLE_FIXE redondante et valide le circuit.
     * CONSERVER  : conserve les deux étapes et valide le circuit tel quel.
     */
    @PostMapping("/{id}/resolution-doublon")
    public ResponseEntity<Map<String, Object>> resoudreDoublon(
            @PathVariable UUID id,
            @RequestBody ResolutionDoublonRequest dto) {
        ModeleCircuit circuit = circuitAdminService.resoudreDoublon(
                id, dto.choix(), dto.etapeId(), dto.gradeDeclencheur());
        return ResponseEntity.ok(Map.of(
                "id",             circuit.getId().toString(),
                "nom",            circuit.getNom(),
                "estModeleNomme", circuit.isEstModeleNomme()));
    }
}
