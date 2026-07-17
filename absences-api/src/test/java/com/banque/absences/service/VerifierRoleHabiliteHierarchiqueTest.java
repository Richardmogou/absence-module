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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * US-AGT-003 — Tests de verifierRoleHabilite() pour le mécanisme HIERARCHIQUE.
 *
 * Le HierarchicalChainResolver est construit avec un RestTemplate intercepté par
 * MockRestServiceServer — aucun appel HTTP réel, aucune BDD.
 */
class VerifierRoleHabiliteHierarchiqueTest {

    private static final String BASE_URL      = "http://keycloak-mock/admin/realms/afb";
    private static final String AGENT_ID      = "agent-test-001";
    private static final String MANAGER_OK    = "manager-test-007";
    private static final String MANAGER_KO    = "manager-test-099";
    private static final UUID   DEMANDE_ID    = UUID.randomUUID();
    private static final String UNITE         = "UNITE-TEST";

    private MockRestServiceServer     mockServer;
    private AbsenceServiceImpl        service;

    // Mocks annexes
    private DemandeAbsenceRepository      demandeRepo;
    private EtapeDemandeSnapshotRepository snapshotRepo;
    private ValidationRepository           validationRepo;
    private ClaimReaderService             claimReader;
    private AbsenceStateMachine            stateMachine;

    @BeforeEach
    void setUp() {
        // ── MockRestServiceServer ────────────────────────────────────────────
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        RestClient restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(restTemplate.getRequestFactory())
                .build();
        HierarchicalChainResolver resolver = new HierarchicalChainResolver(restClient);

        // ── Mocks ────────────────────────────────────────────────────────────
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
                resolver,
                mock(com.banque.absences.repository.SoldeCongeRepository.class),
                mock(SoldeCongeService.class),
                mock(SystemeHabilitations.class),
                mock(MinioStorageService.class),
                mock(com.banque.absences.repository.DocumentMiseEnCongeRepository.class)
        );

        // ── Demande EN_VALIDATION_ETAPE, position 1 (étape Manager) ─────────
        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        injecterUUID(demande, DEMANDE_ID);
        demande.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
        demande.setPositionEtapeCourante(1);
        demande.setUniteIdentifiantExterne(UNITE);
        demande.setDemandeurIdentifiantExterne(AGENT_ID);

        when(demandeRepo.findById(DEMANDE_ID)).thenReturn(Optional.of(demande));
        when(demandeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(validationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Étape position 1 = Manager, mécanisme HIERARCHIQUE, profondeur 1
        EtapeDemandeSnapshot etapeManager = new EtapeDemandeSnapshot();
        etapeManager.setDemandeId(DEMANDE_ID);
        etapeManager.setOrdre(1);
        etapeManager.setLibelle("Manager Direct");
        etapeManager.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
        etapeManager.setPosition(1);

        when(snapshotRepo.findByDemandeIdAndPosition(DEMANDE_ID, 1))
                .thenReturn(Optional.of(etapeManager));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Test 1 : manager-test-007 est le N+1 de agent-test-001 → succès ──────

    @Test
    @DisplayName("US-AGT-003 — Manager N+1 valide : position passe de 1 à 2")
    void managerN1_valider_succes() {
        injecterValidateur(MANAGER_OK);
        when(claimReader.identifiantUtilisateurCourant()).thenReturn(MANAGER_OK);

        // MockRestServiceServer : agent-test-001 → manager-test-007
        // API Admin Keycloak standard : GET /users/{id} → UserRepresentation JSON,
        // l'attribut `manager` porte l'identifiant du N+1 (cf. HierarchicalChainResolver).
        mockServer.expect(requestTo(BASE_URL + "/users/" + AGENT_ID))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess(
                          "{\"id\":\"" + AGENT_ID + "\",\"attributes\":{\"manager\":[\"" + MANAGER_OK + "\"]}}",
                          MediaType.APPLICATION_JSON));

        // State machine incrémente la position
        doAnswer(inv -> {
            DemandeAbsence d = inv.getArgument(0);
            d.setPositionEtapeCourante(d.getPositionEtapeCourante() + 1);
            d.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
            return null;
        }).when(stateMachine).sendEvent(any(), eq(AbsenceEvent.VALIDER));

        DemandeAbsence result = service.enregistrerDecisionEtape(DEMANDE_ID, Decision.VALIDER, null);

        assertThat(result.getPositionEtapeCourante()).isEqualTo(2);
        assertThat(result.getStatut()).isEqualTo(StatutDemande.EN_VALIDATION_ETAPE);
        verify(validationRepo).save(any(Validation.class));
        mockServer.verify();
    }

    // ── Test 2 : manager-test-099 sans lien → 403 ────────────────────────────

    @Test
    @DisplayName("US-AGT-003 — Manager sans lien hiérarchique → ValidateurNonAutoriseException (403)")
    void managerSansLien_valider_403() {
        injecterValidateur(MANAGER_KO);
        when(claimReader.identifiantUtilisateurCourant()).thenReturn(MANAGER_KO);

        // MockRestServiceServer : agent-test-001 → manager-test-007 (pas manager-test-099)
        // API Admin Keycloak standard : GET /users/{id} → UserRepresentation JSON,
        // l'attribut `manager` porte l'identifiant du N+1 (cf. HierarchicalChainResolver).
        mockServer.expect(requestTo(BASE_URL + "/users/" + AGENT_ID))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess(
                          "{\"id\":\"" + AGENT_ID + "\",\"attributes\":{\"manager\":[\"" + MANAGER_OK + "\"]}}",
                          MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
                service.enregistrerDecisionEtape(DEMANDE_ID, Decision.VALIDER, null))
                .isInstanceOf(ValidateurNonAutoriseException.class)
                .hasMessageContaining("N+1");

        verifyNoInteractions(validationRepo);
        verifyNoInteractions(stateMachine);
        mockServer.verify();
    }

    // ── Helpers ───────────────────────────────────────────────

    private static void injecterValidateur(String sub) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .issuer("https://keycloak.banque.com/realms/afb")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim(KeycloakClaims.REALM_ACCESS,
                        Map.of(KeycloakClaims.ROLES, List.of("EMPLOYE")))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
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
