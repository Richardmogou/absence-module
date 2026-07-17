package com.banque.absences.integration;

import com.banque.absences.domain.GrillePermission;
import com.banque.absences.repository.GrillePermissionRepository;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-PERM-001 / US-PERM-002 / US-PERM-004 — Barème permission.
 *
 * Cas 1 : motif EXAMEN_MEDICAL (1 j réglementaire) -> nombreJours=1
 * Cas 2 : motif AUTRE_MOTIF + nombreJours=3 -> nombreJours=3 (conservé)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaremePermissionTest {

    private static final String DA_ID = "da-perm-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenDa;

    @Autowired private MockMvc                    mockMvc;
    @Autowired private GrillePermissionRepository grilleRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-perm").generate();
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
    void setup() throws Exception {
        tokenDa = buildJwt(DA_ID, "DA", List.of("EMPLOYE"));
        // Alimentation de la grille (H2 create-drop, Flyway désactivé en test)
        insererMotif("EXAMEN_MEDICAL", "Convocation examen médical obligatoire", 1, true);
        insererMotif("AUTRE_MOTIF",    "Autre motif exceptionnel",               1, false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 1 — motif barémé (EXAMEN_MEDICAL = 1 jour) → nombreJours forcé à 1
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 1 — PERMISSION EXAMEN_MEDICAL : nombreJours=1 (barème réglementaire)")
    void cas1_motifBureme_dureeReglementaire() throws Exception {
        mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenDa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PERMISSION",
                                  "dateDebut": "2026-10-05",
                                  "motifPermission": "EXAMEN_MEDICAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.nombreJours", is(1)))
                // Lundi 05/10 + 1 jour ouvré : la fin est calculée par le système.
                .andExpect(jsonPath("$.dateFin", is("2026-10-05")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 2 — motif AUTRE_MOTIF → durée saisie par le client conservée
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 2 — PERMISSION AUTRE_MOTIF + nombreJours=3 : durée libre conservée")
    void cas2_autreMotif_dureeLibreConservee() throws Exception {
        mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenDa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PERMISSION",
                                  "dateDebut": "2026-10-06",
                                  "motifPermission": "AUTRES",
                                  "nombreJours": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.nombreJours", is(3)))
                // Mardi 06/10 + 3 jours ouvrés : mar, mer, jeu.
                .andExpect(jsonPath("$.dateFin", is("2026-10-08")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 3 — la période enjambe un week-end : la fin saute au lundi
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 3 — PERMISSION 3 jours à compter d'un jeudi : la fin calculée tombe le lundi")
    void cas3_periodeEnjambeWeekend_finAuLundi() throws Exception {
        mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenDa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PERMISSION",
                                  "dateDebut": "2026-10-08",
                                  "motifPermission": "AUTRES",
                                  "nombreJours": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreJours", is(3)))
                // Jeudi 08/10 (1), vendredi 09/10 (2), week-end sauté, lundi 12/10 (3).
                .andExpect(jsonPath("$.dateFin", is("2026-10-12")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void insererMotif(String codeMotif, String libelle,
                              int dureeJours, boolean justificatifRequis) {
        if (grilleRepo.findByCodeMotif(codeMotif).isEmpty()) {
            GrillePermission g = new GrillePermission();
            g.setCodeMotif(codeMotif);
            g.setLibelle(libelle);
            g.setDureeJours(dureeJours);
            g.setJustificatifRequis(justificatifRequis);
            g.setActif(true);
            grilleRepo.saveAndFlush(g);
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
