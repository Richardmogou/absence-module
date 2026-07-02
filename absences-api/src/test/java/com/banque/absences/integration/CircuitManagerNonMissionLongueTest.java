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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-CIR-006 / US-CIR-007 — Circuit Manager : le DG est sollicite SYSTEMATIQUEMENT,
 * quel que soit le type d'absence (y compris CONGE_ANNUEL).
 *
 * Contraste avec le Circuit Agent : dans ce dernier, l'etape DG n'existe QUE pour
 * les MISSION_LONGUE (via DGConditionnelService). Dans le Circuit Manager, le DG
 * est un validateur HIERARCHIQUE inscrit en dur dans le snapshot des le soumettre.
 *
 * Scenario :
 *   1. Demande CONGE_ANNUEL pour manager-cir006-042
 *   2. Soumission -> Circuit Manager, snapshot 4 etapes (Back-up, Chef, DG, Analyste RH)
 *   3. Validation Back-up -> position 0->1, EN_VALIDATION_ETAPE
 *   4. Validation Chef de processus (N+1) -> position 1->2, EN_VALIDATION_ETAPE
 *      + SQL : le snap a position=2 a role_habilite='DG' (pas encore EN_INSTRUCTION_ANALYSTE_RH)
 *   5. Validation DG (N+2) -> position 2 = derniere intermediaire -> EN_INSTRUCTION_ANALYSTE_RH
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitManagerNonMissionLongueTest {

    private static final String MANAGER_ID = "manager-cir006-042";
    private static final String BACKUP_ID  = "backup-cir006-043";
    private static final String CHEF_ID    = "chef-cir006-050";
    private static final String DG_ID      = "dg-cir006-099";
    private static final String UNITE      = "UNITE-CIR006-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenManager;
    private String tokenBackup;
    private String tokenChef;
    private String tokenDg;

    @Autowired private MockMvc                        mockMvc;
    @Autowired private ObjectMapper                   mapper;
    @Autowired private JdbcTemplate                   jdbcTemplate;
    @Autowired private DemandeAbsenceRepository       demandeRepo;
    @Autowired private ModeleCircuitRepository        circuitRepo;
    @Autowired private EtapeModeleCircuitRepository   etapeModeleRepo;
    @Autowired private RegleAffectationRepository     regleRepo;
    @Autowired private EtapeDemandeSnapshotRepository snapshotRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-cir006").generate();
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
                if (path.contains("/jwks") || path.contains("/certs"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json").setBody(jwksJson);
                if (path.contains("/token"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json").setBody(tokenResponse);
                return new MockResponse().setResponseCode(404);
            }
        });

        // Resolution hierarchique : manager -> chef (N+1), chef -> DG (N+2)
        adminApiMockServer.setDispatcher(new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                String path = req.getPath() == null ? "" : req.getPath();
                if (path.contains(MANAGER_ID + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(CHEF_ID);
                if (path.contains(CHEF_ID + "/manager"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "text/plain").setBody(DG_ID);
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
        tokenManager = buildJwt(MANAGER_ID, "MANAGER", List.of("EMPLOYE", "MANAGER"));
        tokenBackup  = buildJwt(BACKUP_ID,  "AGENT",   List.of("EMPLOYE"));
        tokenChef    = buildJwt(CHEF_ID,    "CHEF_PROCESSUS", List.of("EMPLOYE", "CHEF_PROCESSUS"));
        tokenDg      = buildJwt(DG_ID,      "DG",      List.of("EMPLOYE", "DG"));
        when(doublonService.detecterDoublon(any())).thenReturn(false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    @Test
    @DisplayName("US-CIR-006/007 — Circuit Manager + CONGE_ANNUEL : DG sollicite meme sans Mission longue")
    void circuitManagerCongeAnnuel_dgSolliciteSystematiquement() throws Exception {

        // ── Etape 1 : creer une demande CONGE_ANNUEL (pas Mission longue) ────────
        String body = """
                {"type":"CONGE_ANNUEL","dateDebut":"2026-08-03","dateFin":"2026-08-14"}
                """;

        String response = mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        UUID demandeId = UUID.fromString(mapper.readTree(response).get("id").asText());

        // ── Etape 2 : soumettre -> Circuit Manager ────────────────────────────────
        ModeleCircuit circuit = creerCircuitManagerEnBase();
        when(circuitService.determinerCircuitApplicable(any())).thenReturn(Optional.of(circuit));

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/soumettre")
                        .header("Authorization", "Bearer " + tokenManager))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")))
                .andExpect(jsonPath("$.positionEtapeCourante", is(0)))
                .andExpect(jsonPath("$.circuitId", notNullValue()));

        enrichirSnapshots(demandeId);

        // ── Etape 3 : Back-up valide -> position 0->1 ────────────────────────────
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenBackup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(1)))
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")));

        // ── Etape 4 : Chef de processus valide -> position 1->2 ──────────────────
        // Le statut reste EN_VALIDATION_ETAPE : la position 2 correspond au DG,
        // pas a EN_INSTRUCTION_ANALYSTE_RH. C'est la preuve que le DG est interpelle
        // meme pour un CONGE_ANNUEL dans ce circuit.
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenChef)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(2)))
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")));

        // SQL : le snap courant (position=2) a bien role_habilite='DG'
        assertThat(roleHabiliteAPosition(demandeId, 2)).isEqualTo("DG");

        // ── Etape 5 : DG valide -> EN_INSTRUCTION_ANALYSTE_RH ────────────────────
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDg)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));

        // Une seule validation DG, en mecansime HIERARCHIQUE (jamais DG_CONDITIONNEL)
        assertThat(nombreValidationsDg(demandeId)).isEqualTo(1);
        assertThat(mecanismeValidationDg(demandeId))
                .isEqualTo(MecanismeResolution.HIERARCHIQUE.name());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ModeleCircuit creerCircuitManagerEnBase() {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit Manager - Conge Annuel");
        circuit.setTypeAbsenceCible(TypeAbsence.CONGE_ANNUEL);
        circuit.setActif(true);
        circuit = circuitRepo.saveAndFlush(circuit);

        ajouterEtape(circuit, 0, "Back-up hierarchique",   null,                                 null, null);
        ajouterEtape(circuit, 1, "Chef de Processus",       MecanismeResolution.HIERARCHIQUE,     1,    "CHEF_PROCESSUS");
        ajouterEtape(circuit, 2, "DG",                      MecanismeResolution.HIERARCHIQUE,     2,    "DG");
        ajouterEtape(circuit, 3, "Instruction Analyste RH", MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "ANALYSTE_RH");

        return circuit;
    }

    private void enrichirSnapshots(UUID demandeId) {
        List<EtapeDemandeSnapshot> snaps =
                snapshotRepo.findByDemandeIdOrderByOrdreAsc(demandeId);
        for (EtapeDemandeSnapshot snap : snaps) {
            snap.setPosition(snap.getOrdre());
            if (snap.getOrdre() == 0) {
                snap.setMecanismeResolution(null);
                snap.setValidateurIdentifiantExterne(UNITE);
            } else if (snap.getOrdre() == 1) {
                snap.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
                snap.setRoleHabilite("CHEF_PROCESSUS");
            } else if (snap.getOrdre() == 2) {
                snap.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
                snap.setRoleHabilite("DG");
            } else {
                snap.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
                snap.setRoleHabilite("ANALYSTE_RH");
            }
            snapshotRepo.saveAndFlush(snap);
        }
        DemandeAbsence demande = demandeRepo.findById(demandeId).orElseThrow();
        demande.setUniteIdentifiantExterne(UNITE);
        demandeRepo.saveAndFlush(demande);
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

    private String roleHabiliteAPosition(UUID demandeId, int position) {
        return jdbcTemplate.queryForObject(
                "SELECT role_habilite FROM etape_demande_snapshot " +
                "WHERE demande_id = ? AND position = ?",
                String.class, demandeId, position);
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
