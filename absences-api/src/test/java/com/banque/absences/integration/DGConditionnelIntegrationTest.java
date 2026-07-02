package com.banque.absences.integration;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import com.banque.absences.repository.ModeleCircuitRepository;
import com.banque.absences.repository.RegleAffectationRepository;
import com.banque.absences.security.KeycloakClaims;
import com.banque.absences.service.CircuitDeterminationService;
import com.banque.absences.service.DGConditionnelService;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite d'integration verifiant le comportement de DGConditionnelService
 * dans les 4 combinaisons circuit × type d'absence pertinentes.
 *
 * Cas 1 — Circuit Agent + MISSION_LONGUE  : injection DG conditionnelle DOIT avoir lieu (1 snap DG_CONDITIONNEL)
 * Cas 2 — Circuit Agent + CONGE_ANNUEL    : injection DG conditionnelle NE DOIT PAS avoir lieu
 * Cas 3 — Circuit Manager + MISSION_LONGUE: DG hierarchique ROLE_FIXE, jamais DG_CONDITIONNEL
 * Cas 4 — Circuit Reseau + MISSION_LONGUE : DG ROLE_FIXE_GLOBAL, jamais DG_CONDITIONNEL
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DGConditionnelIntegrationTest {

    // ── Identifiants Circuit Agent ────────────────────────────────────────────
    private static final String AGENT_ID      = "agent-dg-001";
    private static final String BACKUP_AGENT  = "backup-dg-002";
    private static final String MANAGER_ID    = "manager-dg-007";
    private static final String CHEF_ID       = "chef-dg-015";
    private static final String UNITE_AGENT   = "UNITE-DG-001";

    // ── Identifiants Circuit Manager ──────────────────────────────────────────
    private static final String MANAGER_MLG_ID = "manager-dg-042";
    private static final String BACKUP_MGR     = "backup-dg-043";
    private static final String CHEF_MGR       = "chef-dg-050";
    private static final String DG_MGR_ID      = "dg-dg-099";
    private static final String UNITE_MGR      = "UNITE-DG-MGR-001";

    // ── Identifiants Circuit Reseau ───────────────────────────────────────────
    private static final String DA_ID         = "da-dg-060";
    private static final String BACKUP_RESEAU = "backup-dg-061";
    private static final String DR_ID         = "dr-dg-070";
    private static final String DG_RESEAU_ID  = "dg-dg-res-099";
    private static final String RESEAU        = "reseau-dg-littoral";

    // ── Shared DG (utilise aussi pour le cas Agent+MissionLongue) ────────────
    private static final String DG_AGENT_ID   = "dg-dg-agent-099";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    // Tokens Agent circuit
    private String tokenAgent;
    private String tokenBackupAgent;
    private String tokenManager;
    private String tokenChef;
    private String tokenDgAgent;

    // Tokens Manager circuit
    private String tokenManagerMlg;
    private String tokenBackupMgr;
    private String tokenChefMgr;
    private String tokenDgMgr;

    // Tokens Reseau circuit
    private String tokenDa;
    private String tokenBackupReseau;
    private String tokenDr;
    private String tokenDgReseau;

    @Autowired private MockMvc                        mockMvc;
    @Autowired private ObjectMapper                   mapper;
    @Autowired private JdbcTemplate                   jdbcTemplate;
    @Autowired private DemandeAbsenceRepository       demandeRepo;
    @Autowired private ModeleCircuitRepository        circuitRepo;
    @Autowired private EtapeModeleCircuitRepository   etapeModeleRepo;
    @Autowired private RegleAffectationRepository     regleRepo;
    @Autowired private EtapeDemandeSnapshotRepository snapshotRepo;

    @MockBean  private DoublonDetectionService    doublonService;
    @MockBean  private CircuitDeterminationService circuitService;
    @SpyBean   private DGConditionnelService       dgConditionnelService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-dg-suite").generate();
        jwksMockServer     = new MockWebServer();
        adminApiMockServer = new MockWebServer();
        jwksMockServer.start();
        adminApiMockServer.start();

        final String jwksJson =
                new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toString();
        final String tokenResponse = "{\"access_token\":\"fake-admin-token\"," +
                "\"token_type\":\"Bearer\",\"expires_in\":3600}";

        jwksMockServer.setDispatcher(new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                String path = req.getPath() == null ? "" : req.getPath();
                if (path.contains("/jwks") || path.contains("/certs")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(jwksJson);
                }
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
                // Circuit Agent : agent -> manager -> chef
                if (path.contains(AGENT_ID + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(MANAGER_ID);
                if (path.contains(MANAGER_ID + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(CHEF_ID);
                // Circuit Manager : manager -> chef -> DG
                if (path.contains(MANAGER_MLG_ID + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(CHEF_MGR);
                if (path.contains(CHEF_MGR + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(DG_MGR_ID);
                // Circuit Reseau : réseau du demandeur (requis par ROLE_FIXE_SCOPE_RESEAU)
                if (path.contains(DA_ID + "/reseau"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(RESEAU);
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
        // Circuit Agent
        tokenAgent       = buildJwt(AGENT_ID,      "AGENT",          null,   List.of("EMPLOYE"));
        tokenBackupAgent = buildJwt(BACKUP_AGENT,  "AGENT",          null,   List.of("EMPLOYE"));
        tokenManager     = buildJwt(MANAGER_ID,    "MANAGER",        null,   List.of("EMPLOYE", "MANAGER"));
        tokenChef        = buildJwt(CHEF_ID,        "DA",             null,   List.of("EMPLOYE", "CHEF_PROCESSUS"));
        tokenDgAgent     = buildJwt(DG_AGENT_ID,   "DG",             null,   List.of("EMPLOYE", "DG"));
        // Circuit Manager
        tokenManagerMlg  = buildJwt(MANAGER_MLG_ID,"MANAGER",        UNITE_MGR, List.of("EMPLOYE", "MANAGER"));
        tokenBackupMgr   = buildJwt(BACKUP_MGR,    "AGENT",          UNITE_MGR, List.of("EMPLOYE"));
        tokenChefMgr     = buildJwt(CHEF_MGR,      "CHEF_PROCESSUS", null,   List.of("EMPLOYE", "CHEF_PROCESSUS"));
        tokenDgMgr       = buildJwt(DG_MGR_ID,     "DG",             null,   List.of("EMPLOYE", "DG"));
        // Circuit Reseau
        tokenDa          = buildJwt(DA_ID,          "DA",             RESEAU, List.of("EMPLOYE"));
        tokenBackupReseau= buildJwt(BACKUP_RESEAU,  "DA",             RESEAU, List.of("EMPLOYE"));
        tokenDr          = buildJwt(DR_ID,          "DR",             RESEAU, List.of("EMPLOYE", "DR"));
        tokenDgReseau    = buildJwt(DG_RESEAU_ID,   "DG",             null,   List.of("EMPLOYE", "DG"));

        when(doublonService.detecterDoublon(any())).thenReturn(false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cas 1 — Circuit Agent + MISSION_LONGUE
    // L'injection DG conditionnelle DOIT se produire exactement une fois.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cas 1 — Circuit Agent + MISSION_LONGUE : injection DG conditionnelle declenchee une fois")
    void cas1_agentMissionLongue_dgConditionnelInjecte() throws Exception {
        Mockito.clearInvocations(dgConditionnelService);

        UUID demandeId = creerDemande(tokenAgent, "MISSION_LONGUE", "2026-08-01", null, 20);

        ModeleCircuit circuit = creerCircuitAgentEnBase("Circuit Agent - Mission Longue DG Cas1",
                TypeAbsence.MISSION_LONGUE);
        when(circuitService.determinerCircuitApplicable(any())).thenReturn(Optional.of(circuit));

        soumettre(demandeId, tokenAgent);
        enrichirSnapshotsPourCircuitAgent(demandeId, UNITE_AGENT);

        // Back-up -> position 0->1
        valider(demandeId, tokenBackupAgent, 1, "EN_VALIDATION_ETAPE");
        // Manager -> position 1->2
        valider(demandeId, tokenManager, 2, "EN_VALIDATION_ETAPE");

        // Chef de processus -> franchit la derniere etape intermediaire
        // -> DGConditionnelService.necessiteInjection() retourne true (circuit AGENT + MISSION_LONGUE)
        // -> injecterEtapeConditionnelle() cree le snap DG_CONDITIONNEL
        // -> statut reste EN_VALIDATION_ETAPE, position = 3
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenChef)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")));

        // Exactement 1 snap DG_CONDITIONNEL cree
        assertThat(compterSnapshotsDgConditionnel(demandeId)).isEqualTo(1);

        // DG valide l'etape injectee -> EN_INSTRUCTION_ANALYSTE_RH
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDgAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));

        // injecterEtapeConditionnelle() a ete appelee exactement 1 fois
        verify(dgConditionnelService, Mockito.times(1)).injecterEtapeConditionnelle(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cas 2 — Circuit Agent + CONGE_ANNUEL
    // L'injection DG conditionnelle NE DOIT PAS se produire.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cas 2 — Circuit Agent + CONGE_ANNUEL : pas d'injection DG, passage direct a EN_INSTRUCTION_ANALYSTE_RH")
    void cas2_agentCongeAnnuel_pasDgConditionnel() throws Exception {
        Mockito.clearInvocations(dgConditionnelService);

        UUID demandeId = creerDemande(tokenAgent, "CONGE_ANNUEL", "2026-08-03", "2026-08-14", null);

        ModeleCircuit circuit = creerCircuitAgentEnBase("Circuit Agent - Conge Annuel DG Cas2",
                TypeAbsence.CONGE_ANNUEL);
        when(circuitService.determinerCircuitApplicable(any())).thenReturn(Optional.of(circuit));

        soumettre(demandeId, tokenAgent);
        enrichirSnapshotsPourCircuitAgent(demandeId, UNITE_AGENT);

        // Back-up -> 0->1
        valider(demandeId, tokenBackupAgent, 1, "EN_VALIDATION_ETAPE");
        // Manager -> 1->2
        valider(demandeId, tokenManager, 2, "EN_VALIDATION_ETAPE");

        // Chef de processus -> derniere etape intermediaire franchie
        // necessiteInjection() retourne false (type != MISSION_LONGUE)
        // -> passage direct a EN_INSTRUCTION_ANALYSTE_RH
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenChef)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));

        // Aucun snap DG_CONDITIONNEL
        assertThat(compterSnapshotsDgConditionnel(demandeId)).isZero();

        // injecterEtapeConditionnelle() jamais appelee
        verify(dgConditionnelService, never()).injecterEtapeConditionnelle(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cas 3 — Circuit Manager + MISSION_LONGUE
    // DG est une etape HIERARCHIQUE reguliere, pas DG_CONDITIONNEL.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cas 3 — Circuit Manager + MISSION_LONGUE : DG HIERARCHIQUE, jamais DG_CONDITIONNEL")
    void cas3_managerMissionLongue_dgHierarchiqueJamaisConditionnel() throws Exception {
        Mockito.clearInvocations(dgConditionnelService);

        UUID demandeId = creerDemande(tokenManagerMlg, "MISSION_LONGUE", "2026-09-01", null, 20);

        ModeleCircuit circuit = creerCircuitManagerEnBase();
        when(circuitService.determinerCircuitApplicable(any())).thenReturn(Optional.of(circuit));

        soumettre(demandeId, tokenManagerMlg);
        enrichirSnapshotsPourCircuitManager(demandeId);

        // Back-up -> 0->1
        valider(demandeId, tokenBackupMgr, 1, "EN_VALIDATION_ETAPE");
        // Chef -> 1->2
        valider(demandeId, tokenChefMgr, 2, "EN_VALIDATION_ETAPE");

        // 0 validation DG avant l'etape DG
        assertThat(nombreValidationsDg(demandeId)).isZero();

        // DG valide -> EN_INSTRUCTION_ANALYSTE_RH (circuit Manager != AGENT -> pas d'injection)
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDgMgr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));

        assertThat(nombreValidationsDg(demandeId)).isEqualTo(1);
        assertThat(mecanismeValidationDg(demandeId))
                .isEqualTo(MecanismeResolution.HIERARCHIQUE.name());
        assertThat(compterSnapshotsDgConditionnel(demandeId)).isZero();

        verify(dgConditionnelService, never()).injecterEtapeConditionnelle(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cas 4 — Circuit Reseau + MISSION_LONGUE
    // DG est une etape ROLE_FIXE_GLOBAL reguliere, pas DG_CONDITIONNEL.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cas 4 — Circuit Reseau + MISSION_LONGUE : DG ROLE_FIXE_GLOBAL, jamais DG_CONDITIONNEL")
    void cas4_reseauMissionLongue_dgRoleFixeGlobalJamaisConditionnel() throws Exception {
        Mockito.clearInvocations(dgConditionnelService);

        UUID demandeId = creerDemande(tokenDa, "MISSION_LONGUE", "2026-09-01", null, 20);

        ModeleCircuit circuit = creerCircuitReseauEnBase();
        when(circuitService.determinerCircuitApplicable(any())).thenReturn(Optional.of(circuit));

        soumettre(demandeId, tokenDa);
        enrichirSnapshotsPourCircuitReseau(demandeId);

        // Back-up -> 0->1
        valider(demandeId, tokenBackupReseau, 1, "EN_VALIDATION_ETAPE");
        // DR -> 1->2
        valider(demandeId, tokenDr, 2, "EN_VALIDATION_ETAPE");

        // 0 validation DG avant l'etape DG
        assertThat(nombreValidationsDg(demandeId)).isZero();

        // DG valide -> EN_INSTRUCTION_ANALYSTE_RH (circuit Reseau != AGENT -> pas d'injection)
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDgReseau)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));

        assertThat(nombreValidationsDg(demandeId)).isEqualTo(1);
        assertThat(mecanismeValidationDg(demandeId))
                .isEqualTo(MecanismeResolution.ROLE_FIXE_GLOBAL.name());
        assertThat(compterSnapshotsDgConditionnel(demandeId)).isZero();

        verify(dgConditionnelService, never()).injecterEtapeConditionnelle(any());
    }

    // ── Helpers de scenarisation ──────────────────────────────────────────────

    private UUID creerDemande(String token, String type, String dateDebut,
                              String dateFin, Integer nombreJours) throws Exception {
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"").append(type).append("\"");
        json.append(",\"dateDebut\":\"").append(dateDebut).append("\"");
        if (dateFin != null)    json.append(",\"dateFin\":\"").append(dateFin).append("\"");
        if (nombreJours != null) json.append(",\"nombreJours\":").append(nombreJours);
        json.append("}");

        String response = mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(mapper.readTree(response).get("id").asText());
    }

    private void soumettre(UUID demandeId, String token) throws Exception {
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/soumettre")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")))
                .andExpect(jsonPath("$.positionEtapeCourante", is(0)))
                .andExpect(jsonPath("$.circuitId", notNullValue()));
    }

    private void valider(UUID demandeId, String token,
                         int positionAttendue, String statutAttendu) throws Exception {
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(positionAttendue)))
                .andExpect(jsonPath("$.statut", is(statutAttendu)));
    }

    // ── Enrichissement des snapshots ──────────────────────────────────────────

    private void enrichirSnapshotsPourCircuitAgent(UUID demandeId, String unite) {
        List<EtapeDemandeSnapshot> snaps =
                snapshotRepo.findByDemandeIdOrderByOrdreAsc(demandeId);
        for (EtapeDemandeSnapshot s : snaps) {
            s.setPosition(s.getOrdre());
            if (s.getOrdre() == 0) {
                s.setMecanismeResolution(null);
                s.setValidateurIdentifiantExterne(unite);
            } else if (s.getOrdre() == 1 || s.getOrdre() == 2) {
                s.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
            } else {
                s.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
            }
            snapshotRepo.saveAndFlush(s);
        }
        DemandeAbsence demande = demandeRepo.findById(demandeId).orElseThrow();
        demande.setUniteIdentifiantExterne(unite);
        demandeRepo.saveAndFlush(demande);
    }

    private void enrichirSnapshotsPourCircuitManager(UUID demandeId) {
        List<EtapeDemandeSnapshot> snaps =
                snapshotRepo.findByDemandeIdOrderByOrdreAsc(demandeId);
        for (EtapeDemandeSnapshot s : snaps) {
            s.setPosition(s.getOrdre());
            if (s.getOrdre() == 0) {
                s.setMecanismeResolution(null);
                s.setValidateurIdentifiantExterne(UNITE_MGR);
            } else if (s.getOrdre() == 1) {
                s.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
                s.setRoleHabilite("CHEF_PROCESSUS");
            } else if (s.getOrdre() == 2) {
                s.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
                s.setRoleHabilite("DG");
            } else {
                s.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
                s.setRoleHabilite("ANALYSTE_RH");
            }
            snapshotRepo.saveAndFlush(s);
        }
        DemandeAbsence demande = demandeRepo.findById(demandeId).orElseThrow();
        demande.setUniteIdentifiantExterne(UNITE_MGR);
        demandeRepo.saveAndFlush(demande);
    }

    private void enrichirSnapshotsPourCircuitReseau(UUID demandeId) {
        List<EtapeDemandeSnapshot> snaps =
                snapshotRepo.findByDemandeIdOrderByOrdreAsc(demandeId);
        for (EtapeDemandeSnapshot s : snaps) {
            s.setPosition(s.getOrdre());
            if (s.getOrdre() == 0) {
                s.setMecanismeResolution(null);
                s.setValidateurIdentifiantExterne(RESEAU);
            } else if (s.getOrdre() == 1) {
                s.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
                s.setRoleHabilite("DR");
            } else if (s.getOrdre() == 2) {
                s.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_GLOBAL);
                s.setRoleHabilite("DG");
            } else {
                s.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
                s.setRoleHabilite("ANALYSTE_RH");
            }
            snapshotRepo.saveAndFlush(s);
        }
        DemandeAbsence demande = demandeRepo.findById(demandeId).orElseThrow();
        demande.setUniteIdentifiantExterne(RESEAU);
        demandeRepo.saveAndFlush(demande);
    }

    // ── Createurs de circuits ─────────────────────────────────────────────────

    private ModeleCircuit creerCircuitAgentEnBase(String nom, TypeAbsence typeAbsenceCible) {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom(nom);
        circuit.setTypeAbsenceCible(typeAbsenceCible);
        circuit.setActif(true);
        circuit = circuitRepo.saveAndFlush(circuit);

        ajouterEtape(circuit, 0, "Back-up hierarchique",    null,                                 null, null);
        ajouterEtape(circuit, 1, "Manager Direct",           MecanismeResolution.HIERARCHIQUE,     1,    null);
        ajouterEtape(circuit, 2, "Chef de Processus",        MecanismeResolution.HIERARCHIQUE,     2,    null);
        // Les etapes Analyste RH / DRH sont exclues par findIntermediairesOrdonnees (filtre LIKE %ANALYSTE%/%DRH%).
        // On les place aux ordres 10 et 11 pour eviter toute collision avec le snap DG_CONDITIONNEL
        // qu'injecterEtapeConditionnelle() inserera a l'ordre positionCourante+1 = 3.
        ajouterEtape(circuit, 10, "Instruction Analyste RH",  MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "ANALYSTE_RH");
        ajouterEtape(circuit, 11, "Validation DRH",           MecanismeResolution.ROLE_FIXE_GLOBAL, null, null);

        return circuit;
    }

    private ModeleCircuit creerCircuitManagerEnBase() {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit Manager - Mission Longue DG Suite");
        circuit.setTypeAbsenceCible(TypeAbsence.MISSION_LONGUE);
        circuit.setActif(true);
        circuit = circuitRepo.saveAndFlush(circuit);

        ajouterEtape(circuit, 0, "Back-up hierarchique",    null,                                  null, null);
        ajouterEtape(circuit, 1, "Chef de Processus",        MecanismeResolution.HIERARCHIQUE,      1,    "CHEF_PROCESSUS");
        ajouterEtape(circuit, 2, "DG",                       MecanismeResolution.HIERARCHIQUE,      2,    "DG");
        ajouterEtape(circuit, 3, "Instruction Analyste RH",  MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "ANALYSTE_RH");

        return circuit;
    }

    private ModeleCircuit creerCircuitReseauEnBase() {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit Reseau - Mission Longue DG Suite");
        circuit.setTypeAbsenceCible(TypeAbsence.MISSION_LONGUE);
        circuit.setActif(true);
        circuit = circuitRepo.saveAndFlush(circuit);

        ajouterEtape(circuit, 0, "Back-up Reseau",           null,                                  null, null);
        ajouterEtape(circuit, 1, "DR Correspondant",          MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "DR");
        ajouterEtape(circuit, 2, "Validation DG",             MecanismeResolution.ROLE_FIXE_GLOBAL,  null, "DG");
        ajouterEtape(circuit, 3, "Instruction Analyste RH",   MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "ANALYSTE_RH");

        return circuit;
    }

    private void ajouterEtape(ModeleCircuit circuit, int ordre, String libelle,
                              MecanismeResolution mecanisme, Integer profondeur,
                              String roleHabilite) {
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
            regle.setRoleKeycloakCible(roleHabilite);
            regle.setPriorite(1);
            regleRepo.saveAndFlush(regle);
        }
    }

    // ── Requetes SQL d'assertion ──────────────────────────────────────────────

    private long compterSnapshotsDgConditionnel(UUID demandeId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM etape_demande_snapshot " +
                "WHERE demande_id = ? AND mecanisme_resolution = 'DG_CONDITIONNEL'",
                Long.class, demandeId);
        return count == null ? 0L : count;
    }

    private long nombreValidationsDg(UUID demandeId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM validation v
                JOIN etape_demande_snapshot eds ON v.etape_snapshot_id = eds.id
                WHERE v.demande_id = ? AND eds.role_habilite = 'DG'
                """, Long.class, demandeId);
        return count == null ? 0L : count;
    }

    private String mecanismeValidationDg(UUID demandeId) {
        return jdbcTemplate.queryForObject("""
                SELECT eds.mecanisme_resolution FROM validation v
                JOIN etape_demande_snapshot eds ON v.etape_snapshot_id = eds.id
                WHERE v.demande_id = ? AND eds.role_habilite = 'DG'
                """, String.class, demandeId);
    }

    // ── JWT builder ───────────────────────────────────────────────────────────

    private String buildJwt(String subject, String grade, String reseau,
                            List<String> roles) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://keycloak.banque.com/realms/afb")
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim(KeycloakClaims.REALM_ACCESS, Map.of(KeycloakClaims.ROLES, roles))
                .claim(KeycloakClaims.CLAIM_GRADE, grade);
        if (reseau != null) {
            builder.claim(KeycloakClaims.CLAIM_RESEAU, reseau);
        }
        JWTClaimsSet claims = builder.build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
