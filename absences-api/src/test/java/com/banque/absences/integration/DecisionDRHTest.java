package com.banque.absences.integration;

import com.banque.absences.domain.DemandeCongeMaladie;
import com.banque.absences.domain.JustificatifDocument;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.JustificatifDocumentRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

/**
 * US-CIR-011 / US-CIR-012 — Décision finale DRH et génération document.
 *
 * Scénario :
 *   1. CONGE_MALADIE insérée directement en EN_VALIDATION_DRH avec justificatif déposé.
 *   2. POST /validation-drh {"decision":"VALIDER"} -> 200, statut VALIDEE.
 *   3. SQL : SELECT count(*) FROM document_mise_en_conge WHERE demande_id=? -> 1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionDRHTest {

    private static final String DRH_ID = "drh-cir011-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenDrh;

    @Autowired private MockMvc                        mockMvc;
    @Autowired private DemandeAbsenceRepository       demandeRepo;
    @Autowired private JustificatifDocumentRepository justificatifRepo;
    @Autowired private JdbcTemplate                   jdbcTemplate;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-cir011").generate();
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
        tokenDrh = buildJwt(DRH_ID, "DRH", List.of("EMPLOYE", "DRH"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    @Test
    @DisplayName("US-CIR-011/012 — CONGE_MALADIE avec justificatif : DRH valide -> VALIDEE + document genere")
    void validationDRH_congeAvenJustificatif_valide_documentGenere() throws Exception {
        // Demande directement en EN_VALIDATION_DRH avec justificatif
        UUID demandeId = creerDemandeEnValidationDRH();
        deposerJustificatif(demandeId);

        // POST /validation-drh -> 200 statut VALIDEE
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation-drh")
                        .header("Authorization", "Bearer " + tokenDrh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("VALIDEE")));

        // SQL : document généré de façon asynchrone — on attend jusqu'à 15 s
        // (la génération PDF embarque logo + fond + bande Kente en base64, plus lente à froid).
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Long count = jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM document_mise_en_conge WHERE demande_id = ?",
                            Long.class, demandeId);
                    assertThat(count).isEqualTo(1L);
                });

        // L'API expose la LISTE des documents (un par étape génératrice : instruction puis
        // DRH — ici la demande entre directement en DRH, donc un seul), en plus de l'URL
        // legacy qui pointe le plus récent.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v5/demandes/" + demandeId)
                        .header("Authorization", "Bearer " + tokenDrh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsMiseEnConge.length()", is(1)))
                .andExpect(jsonPath("$.documentsMiseEnConge[0].urlDocument").isNotEmpty())
                .andExpect(jsonPath("$.documentsMiseEnConge[0].numero").isNotEmpty())
                .andExpect(jsonPath("$.documentMiseEnCongeUrl").isNotEmpty());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerDemandeEnValidationDRH() {
        DemandeCongeMaladie demande = new DemandeCongeMaladie();
        demande.setDemandeurIdentifiantExterne("da-cir011-001");
        demande.setUniteIdentifiantExterne("UNITE-CIR011");
        demande.setType(TypeAbsence.CONGE_MALADIE);
        demande.setDateDebut(LocalDate.of(2026, 11, 3));
        demande.setDateFin(LocalDate.of(2026, 11, 14));
        demande.setNombreJours(8);
        demande.setStatut(StatutDemande.EN_VALIDATION_DRH);
        demande.setCircuitNom("Circuit Agent - Conge Maladie");
        return demandeRepo.saveAndFlush(demande).getId();
    }

    private void deposerJustificatif(UUID demandeId) {
        JustificatifDocument doc = new JustificatifDocument();
        doc.setDemandeId(demandeId);
        doc.setTypePiece("Certificat medical");
        doc.setUrlFichier("https://storage.banque.com/docs/" + demandeId + "/certificat.pdf");
        doc.setDeposeLe(Instant.now());
        justificatifRepo.saveAndFlush(doc);
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
