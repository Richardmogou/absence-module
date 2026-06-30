package com.banque.absences.controller;

import com.banque.absences.domain.GrillePermission;
import com.banque.absences.dto.CandidatsBackupResponse;
import com.banque.absences.service.BaremePermissionService;
import com.banque.absences.service.ClaimReaderService;
import com.banque.absences.service.HierarchicalChainResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v5/referentiel")
@RequiredArgsConstructor
public class ReferentielController {

    private final BaremePermissionService   baremePermissionService;
    private final ClaimReaderService        claimReaderService;
    private final HierarchicalChainResolver hierarchicalChainResolver;

    @GetMapping("/bareme-permission")
    public ResponseEntity<List<GrillePermission>> bareme() {
        return ResponseEntity.ok(baremePermissionService.listerMotifs());
    }

    @GetMapping("/backup-possibles")
    public ResponseEntity<CandidatsBackupResponse> backupPossibles() {
        String demandeurId = claimReaderService.identifiantUtilisateurCourant();
        String grade       = claimReaderService.lireClaimGrade();
        String unite       = claimReaderService.lireClaimReseau().orElse(null);

        List<String> pairs = hierarchicalChainResolver
                .resoudreColleguesMemeGradeEtUnite(grade, unite)
                .stream()
                .filter(id -> !id.equals(demandeurId))
                .toList();

        String managerDirectId = hierarchicalChainResolver
                .resoudreManagerDirect(demandeurId)
                .orElse(null);

        return ResponseEntity.ok(new CandidatsBackupResponse(pairs, managerDirectId));
    }
}
