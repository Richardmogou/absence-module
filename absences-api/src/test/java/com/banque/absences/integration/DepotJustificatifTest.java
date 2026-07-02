package com.banque.absences.integration;

import com.banque.absences.domain.DemandeCongeMaladie;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-CM-001/002/003 — Dépôt d'un justificatif et instruction du dossier.
 *
 * Scénario :
 *   1. Demande CONGE_MALADIE insérée directement en EN_INSTRUCTION_ANALYSTE_RH.
 *   2. POST /justificatif -> 201, justificatif enregistré.
 *   3. POST /instruction -> 200, statut EN_VALIDATION_DRH (plus de 409).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DepotJustificatifTest {

    private static final String ANALYSTE_ID = "analyste-rh-cm-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAnalyste;

    @Autowired private MockMvc                  mockMvc;
    @Autowired private DemandeAbsenceRepository demandeRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-cm001").generate();
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
        tokenAnalyste = buildJwt(ANALYSTE_ID, "ANALYSTE_RH", List.of("EMPLOYE", "ANALYSTE_RH"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    @Test
    @DisplayName("CONGE_MALADIE : depot justificatif (201) puis instruction reussie (200 EN_VALIDATION_DRH)")
    void depotJustificatifPuisInstruction() throws Exception {
        // Insertion directe d'une demande en attente d'instruction
        UUID demandeId = creerDemandeEnInstruction();

        // Étape 1 : POST /justificatif -> 201
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/justificatif")
                        .header("Authorization", "Bearer " + tokenAnalyste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typePiece": "Certificat medical",
                                  "urlFichier": "https://storage.banque.com/docs/certificat.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.typePiece", is("Certificat medical")))
                .andExpect(jsonPath("$.deposeLe", notNullValue()));

        // Étape 2 : POST /instruction -> 200, statut EN_VALIDATION_DRH (plus de 409)
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/instruction")
                        .header("Authorization", "Bearer " + tokenAnalyste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_DRH")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerDemandeEnInstruction() {
        DemandeCongeMaladie demande = new DemandeCongeMaladie();
        demande.setDemandeurIdentifiantExterne("da-cm-001");
        demande.setUniteIdentifiantExterne("UNITE-CM");
        demande.setType(TypeAbsence.CONGE_MALADIE);
        demande.setDateDebut(LocalDate.of(2026, 10, 1));
        demande.setDateFin(LocalDate.of(2026, 10, 10));
        demande.setNombreJours(7);
        demande.setStatut(StatutDemande.EN_INSTRUCTION_ANALYSTE_RH);
        demande.setCircuitNom("Circuit Agent - Conge Maladie");
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
