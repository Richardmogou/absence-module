package com.banque.absences.integration;

import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import com.banque.absences.repository.ModeleCircuitRepository;
import com.banque.absences.security.KeycloakClaims;
import com.banque.absences.service.CircuitDeterminationService;
import com.banque.absences.service.DoublonDetectionService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-ADM (protection verrou) — Toute tentative de modification
 * d'une étape verrouillée (ANALYSTE_RH ou DRH) retourne 403 ETAPE_VERROUILLEE.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EtapeVerrouilleTest {

    private static final String ADMIN_ID = "admin-rh-verrou-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAdmin;

    /** IDs des étapes verrouillées créées dans @BeforeAll. */
    private UUID etapeAnalysteRhId;
    private UUID etapeDrhId;

    @Autowired private MockMvc                     mockMvc;
    @Autowired private ModeleCircuitRepository     circuitRepo;
    @Autowired private EtapeModeleCircuitRepository etapeRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-verrou001").generate();
        jwksMockServer     = new MockWebServer();
        adminApiMockServer = new MockWebServer();
        jwksMockServer.start();
        adminApiMockServer.start();

        final String jwksJson      = new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toString();
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

        adminApiMockServer.setDispatcher(new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                return new MockResponse().setResponseCode(404);
            }
        });

        registry.add("keycloak.jwks-uri",
                () -> jwksMockServer.url("/realms/afb/protocol/openid-connect/certs").toString());
        registry.add("keycloak.admin-api-base-url",
                () -> adminApiMockServer.url("").toString());
    }

    @BeforeAll
    void setUp() throws Exception {
        tokenAdmin = buildJwt(ADMIN_ID, "ADMIN", List.of("ADMIN_RH"));

        // Crée un circuit directement en base avec les deux étapes verrouillées
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit verrou test");
        circuit.setEstModeleNomme(false);
        circuitRepo.saveAndFlush(circuit);

        etapeAnalysteRhId = creerEtapeVerrouillee(circuit, 0, "ANALYSTE_RH");
        etapeDrhId        = creerEtapeVerrouillee(circuit, 1, "DRH");
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 1 — Modification de l'étape ANALYSTE_RH : 403 ETAPE_VERROUILLEE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 1 — Modification étape ANALYSTE_RH verrouillée : 403 ETAPE_VERROUILLEE")
    void cas1_modificationEtapeAnalysteRH_403() throws Exception {
        mockMvc.perform(put("/api/v5/admin/circuits/" + etapeAnalysteRhId + "/etapes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mecanismeResolution": "ROLE_FIXE_GLOBAL",
                                  "roleHabilite": "AUTRE_ROLE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ETAPE_VERROUILLEE")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 2 — Modification de l'étape DRH : 403 ETAPE_VERROUILLEE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 2 — Modification étape DRH verrouillée : 403 ETAPE_VERROUILLEE")
    void cas2_modificationEtapeDRH_403() throws Exception {
        mockMvc.perform(put("/api/v5/admin/circuits/" + etapeDrhId + "/etapes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mecanismeResolution": "ROLE_FIXE_GLOBAL",
                                  "roleHabilite": "AUTRE_ROLE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ETAPE_VERROUILLEE")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerEtapeVerrouillee(ModeleCircuit circuit, int ordre, String libelle) {
        EtapeModeleCircuit etape = new EtapeModeleCircuit();
        etape.setModeleCircuit(circuit);
        etape.setOrdre(ordre);
        etape.setLibelle(libelle);
        etape.setEstVerrouillable(true);

        RegleAffectation regle = new RegleAffectation();
        regle.setEtapeModeleCircuit(etape);
        regle.setMecanisme(MecanismeResolution.ROLE_FIXE_GLOBAL);
        regle.setRoleKeycloakCible(libelle);
        regle.setPriorite(1);
        etape.getRegles().add(regle);

        return etapeRepo.saveAndFlush(etape).getId();
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
