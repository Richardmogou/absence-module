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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
 * US-MLG-004 — Test d'integration bout-en-bout du Circuit Reseau pour une MISSION_LONGUE.
 *
 * Verifie que DGConditionnelService.injecterEtapeConditionnelle() n'est JAMAIS appele
 * (le DG du Circuit Reseau est un validateur ROLE_FIXE_GLOBAL, pas un DG conditionnel).
 *
 * Scenario :
 *   1. Creation demande MISSION_LONGUE, nombreJours=20, pour da-test-060
 *   2. Soumission -> Circuit Reseau determine, 3 etapes intermediaires
 *   3. Validation par back-up-test-061 (meme reseau) -> position 0->1
 *   4. Validation par dr-test-070 (DR correspondant, meme reseau) -> position 1->2
 *   5. SQL: count(validations DG) = 0 avant l'etape DG
 *   6. Validation par dg-test-099 (DG global) -> EN_INSTRUCTION_ANALYSTE_RH
 *   7. SQL: count(validations DG) = 1, mecanisme = ROLE_FIXE_GLOBAL (jamais DG_CONDITIONNEL)
 *   Spy: verify(dgConditionnelService, never()).injecterEtapeConditionnelle(any())
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitReseauMissionLongueIntegrationTest {

    private static final String DA_ID     = "da-test-060";
    private static final String BACKUP_ID = "back-up-test-061";
    private static final String DR_ID     = "dr-test-070";
    private static final String DG_ID     = "dg-test-099";
    private static final String RESEAU    = "reseau-littoral";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenDa;
    private String tokenBackup;
    private String tokenDr;
    private String tokenDg;

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
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-reseau-mlg").generate();
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

        // GET /users/{demandeurId}/reseau -> reseau-littoral (requis par ROLE_FIXE_SCOPE_RESEAU)
        adminApiMockServer.setDispatcher(new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                String path = req.getPath() == null ? "" : req.getPath();
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
        tokenDa     = buildJwt(DA_ID,     "DA",     RESEAU, List.of("EMPLOYE"));
        tokenBackup = buildJwt(BACKUP_ID, "DA",     RESEAU, List.of("EMPLOYE"));
        tokenDr     = buildJwt(DR_ID,     "DR",     RESEAU, List.of("EMPLOYE", "DR"));
        tokenDg     = buildJwt(DG_ID,     "DG",     null,   List.of("EMPLOYE", "DG"));
        when(doublonService.detecterDoublon(any())).thenReturn(false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    @Test
    @DisplayName("US-MLG-004 — Circuit Reseau MISSION_LONGUE : DG par ROLE_FIXE_GLOBAL, jamais DG_CONDITIONNEL")
    void circuitReseauMissionLongue_dgRoleFixeGlobalJamaisConditionnel() throws Exception {

        // ── Etape 1 : creer la demande ────────────────────────────────────────
        String body = """
                {"type":"MISSION_LONGUE","dateDebut":"2026-09-01","nombreJours":20}
                """;

        String response = mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenDa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        UUID demandeId = UUID.fromString(mapper.readTree(response).get("id").asText());

        // ── Etape 2 : soumettre -> Circuit Reseau ─────────────────────────────
        ModeleCircuit circuit = creerCircuitReseauEnBase();
        when(circuitService.determinerCircuitApplicable(any()))
                .thenReturn(Optional.of(circuit));

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/soumettre")
                        .header("Authorization", "Bearer " + tokenDa))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")))
                .andExpect(jsonPath("$.positionEtapeCourante", is(0)))
                .andExpect(jsonPath("$.circuitId", notNullValue()));

        enrichirSnapshotsPourCircuitReseau(demandeId);

        // ── Etape 3 : Back-up Reseau valide -> position 0->1 ─────────────────
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenBackup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(1)))
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")));

        // ── Etape 4 : DR Correspondant valide -> position 1->2 ───────────────
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(2)))
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")));

        // ── Etape 5 : SQL — 0 validation DG avant l'etape DG ────────────────
        assertThat(nombreValidationsDg(demandeId)).isZero();

        // ── Etape 6 : DG global valide -> EN_INSTRUCTION_ANALYSTE_RH ─────────
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDg)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));

        // ── Etape 7 : SQL — exactement 1 validation DG, mecanisme ROLE_FIXE_GLOBAL ──
        assertThat(nombreValidationsDg(demandeId)).isEqualTo(1);
        assertThat(mecanismeValidationDg(demandeId))
                .isEqualTo(MecanismeResolution.ROLE_FIXE_GLOBAL.name());

        // ── Spy : injecterEtapeConditionnelle() jamais appele ─────────────────
        verify(dgConditionnelService, never()).injecterEtapeConditionnelle(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ModeleCircuit creerCircuitReseauEnBase() {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit Reseau - Mission Longue");
        circuit.setTypeAbsenceCible(TypeAbsence.MISSION_LONGUE);
        circuit.setActif(true);
        circuit = circuitRepo.saveAndFlush(circuit);

        ajouterEtape(circuit, 0, "Back-up Reseau",     null,                                  null, null);
        ajouterEtape(circuit, 1, "DR Correspondant",   MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "DR");
        ajouterEtape(circuit, 2, "Validation DG",      MecanismeResolution.ROLE_FIXE_GLOBAL,  null, "DG");
        ajouterEtape(circuit, 3, "Instruction Analyste RH", MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "ANALYSTE_RH");

        return circuit;
    }

    private void enrichirSnapshotsPourCircuitReseau(UUID demandeId) {
        List<EtapeDemandeSnapshot> snaps =
                snapshotRepo.findByDemandeIdOrderByOrdreAsc(demandeId);
        for (EtapeDemandeSnapshot snap : snaps) {
            snap.setPosition(snap.getOrdre());
            if (snap.getOrdre() == 0) {
                snap.setMecanismeResolution(null);
                snap.setValidateurIdentifiantExterne(RESEAU);
            } else if (snap.getOrdre() == 1) {
                snap.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
                snap.setRoleHabilite("DR");
            } else if (snap.getOrdre() == 2) {
                snap.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_GLOBAL);
                snap.setRoleHabilite("DG");
            } else {
                snap.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
                snap.setRoleHabilite("ANALYSTE_RH");
            }
            snapshotRepo.saveAndFlush(snap);
        }

        DemandeAbsence demande = demandeRepo.findById(demandeId).orElseThrow();
        demande.setUniteIdentifiantExterne(RESEAU);
        demandeRepo.saveAndFlush(demande);
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
