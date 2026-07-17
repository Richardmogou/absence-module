package com.banque.absences.integration;

import com.banque.absences.repository.EtapeModeleCircuitRepository;
import com.banque.absences.repository.ModeleCircuitRepository;
import com.banque.absences.domain.EtapeModeleCircuit;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-ADM-001 / US-ADM-002 — Composition libre d'un circuit par l'Administrateur RH.
 *
 * Cas 1 : étape DG_CONDITIONNEL explicite -> 400 ETAPE_INVALIDE.
 * Cas 2 : circuit valide -> 201, étapes ANALYSTE_RH et DRH ajoutées
 *          automatiquement en dernières positions avec estVerrouillable=true.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitAdminTest {

    private static final String ADMIN_ID = "admin-rh-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAdmin;
    private String tokenEmploye;

    @Autowired private MockMvc                     mockMvc;
    @Autowired private ModeleCircuitRepository     circuitRepo;
    @Autowired private EtapeModeleCircuitRepository etapeRepo;

    @MockBean private DoublonDetectionService         doublonService;
    @MockBean private CircuitDeterminationService     circuitService;
    @MockBean private com.banque.absences.service.CircuitCoherenceCheckerService coherenceChecker;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-adm001").generate();
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
    void initTokens() throws Exception {
        tokenAdmin  = buildJwt(ADMIN_ID, "ADMIN", List.of("ADMIN_RH"));
        tokenEmploye = buildJwt("employe-001", "DA", List.of("EMPLOYE"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 1 — Étape DG_CONDITIONNEL explicite : 400 ETAPE_INVALIDE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 1 — DG_CONDITIONNEL composé explicitement : 400 ETAPE_INVALIDE")
    void cas1_etapeDgConditionnelExplicite_400() throws Exception {
        mockMvc.perform(post("/api/v5/admin/circuits")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nom": "Circuit invalide",
                                  "typeAbsenceCible": "CONGE_ANNUEL",
                                  "gradeDeclencheur": "DA",
                                  "etapesIntermediaires": [
                                    {
                                      "mecanismeResolution": "DG_CONDITIONNEL",
                                      "roleHabilite": "DG"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ETAPE_INVALIDE")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 2 — Circuit valide : seules les étapes intermédiaires demandées sont créées
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * La création ne pose QUE les étapes intermédiaires du payload.
     *
     * <p>Ce test exigeait auparavant que {@code creerCircuit} ajoute d'office une étape
     * ANALYSTE_RH et une étape DRH verrouillées. Ce n'est plus le comportement : ces étapes
     * sont posées par migration (V15) sur les circuits standard, et la machine à états les
     * traite hors du flux intermédiaire. Les injecter à la création reviendrait à les
     * dupliquer.
     */
    @Test
    @DisplayName("Cas 2 — Circuit valide : seules les étapes intermédiaires du payload sont créées")
    void cas2_circuitValide_seulesEtapesIntermediairesCreees() throws Exception {
        String response = mockMvc.perform(post("/api/v5/admin/circuits")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nom": "Circuit test ADM-001",
                                  "typeAbsenceCible": "CONGE_ANNUEL",
                                  "gradeDeclencheur": "DA",
                                  "etapesIntermediaires": [
                                    {
                                      "mecanismeResolution": "HIERARCHIQUE",
                                      "roleHabilite": "MANAGER"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom", is("Circuit test ADM-001")))
                .andReturn().getResponse().getContentAsString();

        UUID circuitId = UUID.fromString(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(response).get("id").asText());

        // Une seule étape : celle du payload. Aucune étape fixe n'est injectée.
        var etapes = etapeRepo.findByModeleCircuitIdOrderByOrdreAsc(circuitId);
        assertThat(etapes).hasSize(1);

        var intermediaire = etapes.get(0);
        assertThat(intermediaire.getOrdre()).isZero();
        assertThat(intermediaire.isEstVerrouillable()).isFalse();

        // Garde-fou : aucune étape RH/DRH n'est ajoutée d'office par la création.
        assertThat(etapes).extracting(EtapeModeleCircuit::getLibelle)
                .doesNotContain("ANALYSTE_RH", "DRH");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

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
