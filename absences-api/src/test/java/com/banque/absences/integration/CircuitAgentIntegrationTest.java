package com.banque.absences.integration;

import com.banque.absences.domain.*;
import com.banque.absences.repository.*;
import com.banque.absences.security.KeycloakClaims;
import com.banque.absences.service.CircuitDeterminationService;
import com.banque.absences.service.DoublonDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * US-CIR-005 — Test d'integration bout-en-bout du Circuit Agent.
 *
 * Scenario :
 *   1. Creation demande CONGE_ANNUEL pour agent-test-001
 *   2. Soumission -> Circuit Agent, snapshot 5 etapes
 *   3. Back-up (meme unite) valide -> position 0->1
 *   4. Manager N+1 valide -> position 1->2
 *   5. Chef de processus N+2 valide -> EN_INSTRUCTION_ANALYSTE_RH
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitAgentIntegrationTest {

    private static final String AGENT_ID   = "agent-test-001";
    private static final String BACKUP_ID  = "backup-test-002";
    private static final String MANAGER_ID = "manager-test-007";
    private static final String CHEF_ID    = "chef-processus-test-015";
    private static final String UNITE      = "UNITE-DAKAR-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey         rsaKey;

    private String tokenAgent;
    private String tokenBackup;
    private String tokenManager;
    private String tokenChef;
    private UUID   demandeId;

    @Autowired private MockMvc                          mockMvc;
    @Autowired private ObjectMapper                     mapper;
    @Autowired private DemandeAbsenceRepository         demandeRepo;
    @Autowired private ModeleCircuitRepository          circuitRepo;
    @Autowired private EtapeModeleCircuitRepository     etapeModeleRepo;
    @Autowired private RegleAffectationRepository       regleRepo;
    @Autowired private EtapeDemandeSnapshotRepository   snapshotRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key").generate();
        jwksMockServer     = new MockWebServer();
        adminApiMockServer = new MockWebServer();
        jwksMockServer.start();
        adminApiMockServer.start();

        final String jwksJson =
                new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toString();

        // Token OAuth2 fictif pour le client_credentials grant
        final String tokenResponse = "{\"access_token\":\"fake-admin-token\"," +
                "\"token_type\":\"Bearer\",\"expires_in\":3600}";

        jwksMockServer.setDispatcher(new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                String path = req.getPath() == null ? "" : req.getPath();
                // JWKS endpoint
                if (path.contains("/jwks") || path.contains("/certs")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(jwksJson);
                }
                // Token endpoint (client_credentials pour KeycloakAdminClientConfig)
                if (path.contains("/token")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(tokenResponse);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        adminApiMockServer.setDispatcher(new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                String path = req.getPath() == null ? "" : req.getPath();
                if (path.contains(AGENT_ID + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(MANAGER_ID);
                if (path.contains(MANAGER_ID + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(CHEF_ID);
                return new MockResponse().setResponseCode(404);
            }
        });

        registry.add("keycloak.jwks-uri",
                () -> jwksMockServer.url("/realms/afb/protocol/openid-connect/certs").toString());
        registry.add("keycloak.admin-api-base-url",
                () -> adminApiMockServer.url("").toString());
    }

    @BeforeAll
    void initTokensEtMocks() throws Exception {
        tokenAgent   = buildJwt(AGENT_ID,   "AGENT",   List.of("EMPLOYE"));
        tokenBackup  = buildJwt(BACKUP_ID,  "AGENT",   List.of("EMPLOYE"));
        tokenManager = buildJwt(MANAGER_ID, "MANAGER", List.of("EMPLOYE", "MANAGER"));
        tokenChef    = buildJwt(CHEF_ID,    "DA",      List.of("EMPLOYE", "CHEF_PROCESSUS"));
        when(doublonService.detecterDoublon(any())).thenReturn(false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ── Etape 1 : creer la demande ────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("1. POST /demandes -> 201, statut=BROUILLON")
    void etape1_creerDemande() throws Exception {
        String body = """
                {"type":"CONGE_ANNUEL","dateDebut":"2026-08-03","dateFin":"2026-08-14"}
                """;

        String response = mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        demandeId = UUID.fromString(mapper.readTree(response).get("id").asText());
    }

    // ── Etape 2 : soumettre ───────────────────────────────────────────────────

    @Test @Order(2)
    @DisplayName("2. POST /soumettre -> EN_VALIDATION_ETAPE, position=0")
    void etape2_soumettre() throws Exception {
        ModeleCircuit circuit = creerCircuitAgentEnBase();
        when(circuitService.determinerCircuitApplicable(any()))
                .thenReturn(Optional.of(circuit));

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/soumettre")
                        .header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")))
                .andExpect(jsonPath("$.positionEtapeCourante", is(0)))
                .andExpect(jsonPath("$.circuitId", notNullValue()));

        // Enrichir les snapshots et la demande — saveAndFlush pour commit immediat
        List<EtapeDemandeSnapshot> snaps =
                snapshotRepo.findByDemandeIdOrderByOrdreAsc(demandeId);
        for (EtapeDemandeSnapshot s : snaps) {
            s.setPosition(s.getOrdre());
            if (s.getOrdre() == 0) {
                s.setMecanismeResolution(null);
                s.setValidateurIdentifiantExterne(UNITE);
            } else if (s.getOrdre() == 1 || s.getOrdre() == 2) {
                s.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
            } else {
                s.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
            }
            snapshotRepo.saveAndFlush(s);
        }

        DemandeAbsence demande = demandeRepo.findById(demandeId).orElseThrow();
        demande.setUniteIdentifiantExterne(UNITE);
        demandeRepo.saveAndFlush(demande);
    }

    // ── Etape 3 : Back-up valide ──────────────────────────────────────────────

    @Test @Order(3)
    @DisplayName("3. POST /validation par back-up -> positionEtapeCourante=1")
    void etape3_backupValide() throws Exception {
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenBackup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(1)))
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")));
    }

    // ── Etape 4 : Manager N+1 valide ─────────────────────────────────────────

    @Test @Order(4)
    @DisplayName("4. POST /validation par manager-test-007 (N+1) -> positionEtapeCourante=2")
    void etape4_managerN1Valide() throws Exception {
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(2)))
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")));
    }

    // ── Etape 5 : Chef de processus N+2 -> EN_INSTRUCTION_ANALYSTE_RH ─────────

    @Test @Order(5)
    @DisplayName("5. POST /validation par chef-processus-test-015 (N+2) -> EN_INSTRUCTION_ANALYSTE_RH")
    void etape5_chefProcessusValide() throws Exception {
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenChef)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    ModeleCircuit creerCircuitAgentEnBase() {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit Agent — Conge Annuel");
        circuit.setTypeAbsenceCible(TypeAbsence.CONGE_ANNUEL);
        circuit.setActif(true);
        circuit = circuitRepo.saveAndFlush(circuit);

        ajouterEtape(circuit, 0, "Back-up hierarchique",   null,                                null);
        ajouterEtape(circuit, 1, "Manager Direct",          MecanismeResolution.HIERARCHIQUE,    1);
        ajouterEtape(circuit, 2, "Chef de Processus",       MecanismeResolution.HIERARCHIQUE,    2);
        ajouterEtape(circuit, 3, "Instruction Analyste RH", MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null);
        ajouterEtape(circuit, 4, "Validation DRH",          MecanismeResolution.ROLE_FIXE_GLOBAL, null);

        return circuit;
    }

    void ajouterEtape(ModeleCircuit circuit, int ordre, String libelle,
                      MecanismeResolution mecanisme, Integer profondeur) {
        EtapeModeleCircuit etape = new EtapeModeleCircuit();
        etape.setModeleCircuit(circuit);
        etape.setOrdre(ordre);
        etape.setLibelle(libelle);
        etape.setDelaiJours(5);
        etape.setEstVerrouillable(false);
        etape = etapeModeleRepo.saveAndFlush(etape);

        if (mecanisme != null) {
            RegleAffectation regle = new RegleAffectation();
            regle.setEtapeModeleCircuit(etape);
            regle.setMecanisme(mecanisme);
            regle.setProfondeurHierarchique(profondeur);
            regle.setPriorite(1);
            regleRepo.saveAndFlush(regle);
        }
    }

    private String buildJwt(String subject, String grade, List<String> roles) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://keycloak.banque.com/realms/afb")
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim(KeycloakClaims.REALM_ACCESS, Map.of(KeycloakClaims.ROLES, roles))
                .claim(KeycloakClaims.CLAIM_GRADE, grade)
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
