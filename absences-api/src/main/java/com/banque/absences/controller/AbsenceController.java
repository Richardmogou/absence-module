package com.banque.absences.controller;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.JustificatifDocument;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.dto.AbsenceRequest;
import com.banque.absences.dto.AbsenceResponse;
import com.banque.absences.dto.CreationDemandeRequest;
import com.banque.absences.dto.CreationProlongationRequest;
import com.banque.absences.dto.DepotJustificatifRequest;
import com.banque.absences.dto.ModificationDemandeRequest;
import com.banque.absences.dto.PreviewDemandeResponse;
import com.banque.absences.dto.RetourAnticipeRequest;
import com.banque.absences.dto.StatutUpdateRequest;
import com.banque.absences.dto.ValidationEtapeRequest;
import com.banque.absences.dto.ValidationEtapeRequest.Decision;
import com.banque.absences.service.AbsenceService;
import com.banque.absences.service.ClaimReaderService;
import com.banque.absences.service.InstructionAnalysteRHService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v5/demandes")
@RequiredArgsConstructor
public class AbsenceController {

    private final AbsenceService service;
    private final InstructionAnalysteRHService instructionAnalysteRHService;
    private final ClaimReaderService claimReaderService;

    @PostMapping
    public ResponseEntity<DemandeAbsence> creer(@Valid @RequestBody CreationDemandeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerDemande(request));
    }

    @GetMapping
    public List<AbsenceResponse> findAll(@RequestParam(required = false) StatutDemande statut) {
        if (statut != null) return service.findByStatut(statut);
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AbsenceResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @GetMapping("/demandeur/{demandeurId}")
    public List<AbsenceResponse> findByDemandeur(
            @PathVariable String demandeurId,
            @RequestParam(required = false) StatutDemande statut) {
        if (statut != null) return service.findByDemandeurAndStatut(demandeurId, statut);
        return service.findByDemandeur(demandeurId);
    }

    @GetMapping("/moi/backup")
    public List<AbsenceResponse> mesDemandesBackup() {
        String id = claimReaderService.identifiantUtilisateurCourant();
        return service.findByBackup(id);
    }

    @GetMapping("/a-valider")
    public List<AbsenceResponse> demandesAValider() {
        return service.findDemandesAValider();
    }

    @GetMapping("/moi/solde")
    public ResponseEntity<com.banque.absences.dto.SoldeCongeResponse> monSolde() {
        String id = claimReaderService.identifiantUtilisateurCourant();
        return ResponseEntity.ok(service.findSoldeByDemandeur(id));
    }

    @GetMapping("/moi")
    public List<AbsenceResponse> mesDemandes(
            @RequestParam(required = false) StatutDemande statut) {
        String id = claimReaderService.identifiantUtilisateurCourant();
        if (statut != null) return service.findByDemandeurAndStatut(id, statut);
        return service.findByDemandeur(id);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<PreviewDemandeResponse> preview(@PathVariable UUID id) {
        return ResponseEntity.ok(service.previsualiser(id));
    }

    @PostMapping("/{id}/soumettre")
    public ResponseEntity<DemandeAbsence> soumettre(@PathVariable UUID id,
                                                    @RequestParam(required = false, defaultValue = "false") boolean confirmDoublon) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.soumettre(id, confirmDoublon));
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasRole('ADMIN_RH')")
    public AbsenceResponse updateStatut(@PathVariable UUID id,
                                        @Valid @RequestBody StatutUpdateRequest request) {
        return service.updateStatut(id, request);
    }

    @PostMapping("/{id}/validation")
    public ResponseEntity<DemandeAbsence> valider(@PathVariable UUID id,
                                                  @Valid @RequestBody ValidationEtapeRequest dto) {
        return ResponseEntity.ok(
                service.enregistrerDecisionEtape(id, dto.decision(), dto.motif()));
    }

    @PostMapping(value = "/{id}/justificatif", consumes = "multipart/form-data")
    public ResponseEntity<JustificatifDocument> deposer(
            @PathVariable UUID id,
            @RequestParam("typePiece") String typePiece,
            @RequestParam("fichier") org.springframework.web.multipart.MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.deposerJustificatif(id, typePiece, fichier));
    }

    @PostMapping("/{id}/instruction")
    public ResponseEntity<DemandeAbsence> instruire(
            @PathVariable UUID id,
            @RequestBody(required = false) com.banque.absences.dto.InstructionRequest body) {
        return ResponseEntity.ok(
                instructionAnalysteRHService.instruire(id, body != null ? body.dateDebut() : null));
    }

    @PostMapping("/{id}/prolongation-maternite")
    public ResponseEntity<DemandeAbsence> prolonger(
            @PathVariable UUID id,
            @RequestBody CreationProlongationRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerProlongation(id, dto));
    }

    @PostMapping("/{id}/validation-drh")
    public ResponseEntity<DemandeAbsence> validerDRH(
            @PathVariable UUID id,
            @Valid @RequestBody ValidationEtapeRequest dto) {
        return ResponseEntity.ok(
                service.enregistrerDecisionDRH(id, dto.decision(), dto.motif(), dto.nombreJoursAjuste()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DemandeAbsence> modifier(@PathVariable UUID id,
                                                    @Valid @RequestBody ModificationDemandeRequest dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @PostMapping("/{id}/retour-anticipe")
    public ResponseEntity<DemandeAbsence> retourAnticipe(
            @PathVariable UUID id,
            @Valid @RequestBody RetourAnticipeRequest dto) {
        return ResponseEntity.ok(service.retourAnticipe(id, dto.dateRetourEffective()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
