package com.banque.absences.service;

import com.banque.absences.domain.*;
import com.banque.absences.dto.ValidationEtapeRequest.Decision;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.repository.ValidationRepository;
import com.banque.absences.security.KeycloakClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * US-CIR-004 / US-AGT-004 — Tests unitaires de enregistrerDecisionEtape.
 * Aucun contexte Spring, aucune BDD.
 */
class EnregistrerDecisionEtapeTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────
    private DemandeAbsenceRepository      demandeRepo;
    private EtapeDemandeSnapshotRepository snapshotRepo;
    private ValidationRepository           validationRepo;
    private ClaimReaderService             claimReader;
    private AbsenceStateMachine            stateMachine;
    private AbsenceServiceImpl             service;

    private static final UUID DEMANDE_ID = UUID.randomUUID();
    private static final String UNITE     = "UNITE-DAKAR-001";
    private static final String VALIDATEUR_MEME_UNITE   = "backup-dakar-001";
    private static final String VALIDATEUR_AUTRE_UNITE  = "backup-abidjan-999";

    @BeforeEach
    void setUp() {
        demandeRepo    = mock(DemandeAbsenceRepository.class);
        snapshotRepo   = mock(EtapeDemandeSnapshotRepository.class);
        validationRepo = mock(ValidationRepository.class);
        claimReader    = mock(ClaimReaderService.class);
        stateMachine   = mock(AbsenceStateMachine.class);

        service = new AbsenceServiceImpl(
                mock(com.banque.absences.repository.AbsenceRepository.class),
                demandeRepo,
                mock(com.banque.absences.repository.EtapeModeleCircuitRepository.class),
                snapshotRepo,
                validationRepo,
                mock(com.banque.absences.repository.AuditOverrideStatutRepository.class),
                mock(com.banque.absences.repository.JustificatifDocumentRepository.class),
                mock(DocumentMiseEnCongeService.class),
                mock(BaremePermissionService.class),
                claimReader,
                mock(DoublonDetectionService.class),
                mock(CircuitDeterminationService.class),
                stateMachine,
                mock(HierarchicalChainResolver.class),
                mock(com.banque.absences.repository.SoldeCongeRepository.class),
                mock(SoldeCongeService.class),
                mock(SystemeHabilitations.class),
                mock(MinioStorageService.class),
                mock(com.banque.absences.repository.DocumentMiseEnCongeRepository.class)
        );

        // Demande EN_VALIDATION_ETAPE, position 0, unité DAKAR
        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        injecterUUID(demande, DEMANDE_ID);
        demande.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
        demande.setPositionEtapeCourante(0);
        demande.setUniteIdentifiantExterne(UNITE);
        demande.setDemandeurIdentifiantExterne("agent-dakar-42");

        when(demandeRepo.findById(DEMANDE_ID)).thenReturn(Optional.of(demande));
        when(demandeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(validationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Étape Back-up position 0 — même unité que le demandeur
        EtapeDemandeSnapshot etapeBackup = new EtapeDemandeSnapshot();
        etapeBackup.setDemandeId(DEMANDE_ID);
        etapeBackup.setOrdre(0);
        etapeBackup.setLibelle("Back-up hiérarchique");
        etapeBackup.setValidateurIdentifiantExterne(UNITE); // champ utilisé pour la comparaison d'unité
        when(snapshotRepo.findByDemandeIdAndPosition(DEMANDE_ID, 0))
                .thenReturn(Optional.of(etapeBackup));

        // Étapes intermédiaires pour la state machine
        List<EtapeDemandeSnapshot> etapes = List.of(
                etape(DEMANDE_ID, 0, "Back-up hiérarchique"),
                etape(DEMANDE_ID, 1, "Manager Direct"),
                etape(DEMANDE_ID, 2, "Chef de Processus")
        );
        when(snapshotRepo.findIntermediairesOrdonnees(DEMANDE_ID)).thenReturn(etapes);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Test 1 : Back-up même unité → position passe de 0 à 1 ────────────────

    @Test
    @DisplayName("Back-up même unité + VALIDER → 200, positionEtapeCourante passe de 0 à 1")
    void backupMemeUnite_valider_positionIncrement() {
        injecterValidateur(VALIDATEUR_MEME_UNITE);
        when(claimReader.identifiantUtilisateurCourant()).thenReturn(VALIDATEUR_MEME_UNITE);

        // La state machine modifie la demande (simulée ici pour tester le service)
        doAnswer(inv -> {
            DemandeAbsence d = inv.getArgument(0);
            d.setPositionEtapeCourante(d.getPositionEtapeCourante() + 1);
            d.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
            return null;
        }).when(stateMachine).sendEvent(any(), eq(AbsenceEvent.VALIDER));

        DemandeAbsence result = service.enregistrerDecisionEtape(DEMANDE_ID, Decision.VALIDER, null);

        assertThat(result.getPositionEtapeCourante()).isEqualTo(1);
        assertThat(result.getStatut()).isEqualTo(StatutDemande.EN_VALIDATION_ETAPE);
        verify(validationRepo).save(any(Validation.class));
        verify(stateMachine).sendEvent(any(), eq(AbsenceEvent.VALIDER));
    }

    // ── Test 2 : autre unité → 403 ValidateurNonAutoriseException ─────────────

    @Test
    @DisplayName("Validateur autre unité → ValidateurNonAutoriseException (403 VALIDATEUR_NON_AUTORISE)")
    void autreUnite_valider_403() {
        injecterValidateur(VALIDATEUR_AUTRE_UNITE);
        when(claimReader.identifiantUtilisateurCourant()).thenReturn(VALIDATEUR_AUTRE_UNITE);

        // L'étape retourne une unité différente
        EtapeDemandeSnapshot etapeAutreUnite = new EtapeDemandeSnapshot();
        etapeAutreUnite.setDemandeId(DEMANDE_ID);
        etapeAutreUnite.setOrdre(0);
        etapeAutreUnite.setLibelle("Back-up hiérarchique");
        etapeAutreUnite.setValidateurIdentifiantExterne("UNITE-ABIDJAN-999"); // unité différente
        when(snapshotRepo.findByDemandeIdAndPosition(DEMANDE_ID, 0))
                .thenReturn(Optional.of(etapeAutreUnite));

        assertThatThrownBy(() ->
                service.enregistrerDecisionEtape(DEMANDE_ID, Decision.VALIDER, null))
                .isInstanceOf(ValidateurNonAutoriseException.class)
                .hasMessageContaining("VALIDATEUR_NON_AUTORISE");

        verifyNoInteractions(validationRepo);
        verifyNoInteractions(stateMachine);
    }

    // ── Test 3 : rejet sans motif → MotifRequisException ─────────────────────

    @Test
    @DisplayName("REJETER sans motif → MotifRequisException (400)")
    void rejeterSansMotif_400() {
        injecterValidateur(VALIDATEUR_MEME_UNITE);
        when(claimReader.identifiantUtilisateurCourant()).thenReturn(VALIDATEUR_MEME_UNITE);

        assertThatThrownBy(() ->
                service.enregistrerDecisionEtape(DEMANDE_ID, Decision.REJETER, ""))
                .isInstanceOf(MotifRequisException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void injecterValidateur(String sub) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .issuer("https://keycloak.banque.com/realms/afb")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim(KeycloakClaims.REALM_ACCESS, Map.of(KeycloakClaims.ROLES, List.of("EMPLOYE")))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static EtapeDemandeSnapshot etape(UUID demandeId, int ordre, String libelle) {
        EtapeDemandeSnapshot e = new EtapeDemandeSnapshot();
        e.setDemandeId(demandeId);
        e.setOrdre(ordre);
        e.setLibelle(libelle);
        return e;
    }

    private static void injecterUUID(DemandeAbsence demande, UUID id) {
        try {
            var field = DemandeAbsence.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(demande, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
