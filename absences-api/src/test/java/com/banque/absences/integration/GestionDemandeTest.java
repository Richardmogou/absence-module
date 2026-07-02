package com.banque.absences.integration;

import com.banque.absences.domain.DemandeCongeAnnuel;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
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
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-GES-001 / US-GES-002 — Modification et suppression d'une demande selon statut.
 *
 * Cas 1 : PUT /{id}  sur demande EN_VALIDATION_ETAPE -> 409 MODIFICATION_IMPOSSIBLE.
 * Cas 2 : DELETE /{id} sur demande EN_VALIDATION_ETAPE -> 409 SUPPRESSION_IMPOSSIBLE.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GestionDemandeTest {

    private static final String AGENT_ID = "agent-ges-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAgent;

    @Autowired private MockMvc                  mockMvc;
    @Autowired private DemandeAbsenceRepository demandeRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-ges001").generate();
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
        tokenAgent = buildJwt(AGENT_ID, "DA", List.of("EMPLOYE"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 1 — PUT sur demande EN_VALIDATION_ETAPE : 409 MODIFICATION_IMPOSSIBLE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 1 — Modification d'une demande EN_VALIDATION_ETAPE : 409 MODIFICATION_IMPOSSIBLE")
    void cas1_modificationDemandeEnValidationEtape_409() throws Exception {
        UUID demandeId = creerDemandeEnValidationEtape();

        mockMvc.perform(put("/api/v5/demandes/" + demandeId)
                        .header("Authorization", "Bearer " + tokenAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dateDebut": "2026-08-01",
                                  "dateFin":   "2026-08-10",
                                  "nombreJours": 8
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("MODIFICATION_IMPOSSIBLE")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 2 — DELETE sur demande EN_VALIDATION_ETAPE : 409 SUPPRESSION_IMPOSSIBLE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 2 — Suppression d'une demande EN_VALIDATION_ETAPE : 409 SUPPRESSION_IMPOSSIBLE")
    void cas2_suppressionDemandeEnValidationEtape_409() throws Exception {
        UUID demandeId = creerDemandeEnValidationEtape();

        mockMvc.perform(delete("/api/v5/demandes/" + demandeId)
                        .header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SUPPRESSION_IMPOSSIBLE")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerDemandeEnValidationEtape() {
        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        demande.setDemandeurIdentifiantExterne(AGENT_ID);
        demande.setUniteIdentifiantExterne("UNITE-GES-001");
        demande.setType(TypeAbsence.CONGE_ANNUEL);
        demande.setDateDebut(LocalDate.of(2026, 7, 1));
        demande.setDateFin(LocalDate.of(2026, 7, 15));
        demande.setNombreJours(11);
        demande.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
        return demandeRepo.saveAndFlush(demande).getId();
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
