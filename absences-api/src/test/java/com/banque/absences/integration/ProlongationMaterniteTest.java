package com.banque.absences.integration;

import com.banque.absences.domain.DemandeCongeMaternite;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.ModeleCircuitRepository;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-MAT-001 à US-MAT-004 — Congé maternité et prolongation.
 *
 * Cas 1 : prolongation depuis une initiale VALIDEE
 *         -> 201, circuitId == circuitId de l'initiale (assertEquals UUID).
 * Cas 2 : prolongation depuis une initiale EN_VALIDATION_ETAPE
 *         -> 409 PROLONGATION_NON_AUTORISEE.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProlongationMaterniteTest {

    private static final String DA_ID = "da-mat-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenDa;

    @Autowired private MockMvc                    mockMvc;
    @Autowired private ObjectMapper               mapper;
    @Autowired private DemandeAbsenceRepository   demandeRepo;
    @Autowired private ModeleCircuitRepository    circuitRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-mat").generate();
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
        tokenDa = buildJwt(DA_ID, "DA", List.of("EMPLOYE"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 1 — Initiale VALIDEE : prolongation créée, circuitId identique
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 1 — Prolongation depuis initiale VALIDEE : 201 + circuitId copié")
    void cas1_prolongationDepuisInitialeValidee_circuitIdCopie() throws Exception {
        UUID circuitId = creerCircuitEnBase();
        UUID initialeId = creerInitialeValidee(circuitId);

        String response = mockMvc.perform(post("/api/v5/demandes/" + initialeId + "/prolongation-maternite")
                        .header("Authorization", "Bearer " + tokenDa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.nombreJours", is(42)))
                .andReturn().getResponse().getContentAsString();

        UUID prolongationCircuitId = UUID.fromString(
                mapper.readTree(response).get("circuitId").asText());
        assertThat(prolongationCircuitId)
                .as("circuitId de la prolongation == circuitId de l'initiale")
                .isEqualTo(circuitId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 2 — Initiale non VALIDEE : 409 PROLONGATION_NON_AUTORISEE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 2 — Prolongation depuis initiale EN_VALIDATION_ETAPE : 409 PROLONGATION_NON_AUTORISEE")
    void cas2_prolongationDepuisInitialeNonValidee_409() throws Exception {
        UUID initialeId = creerInitialeEnValidationEtape();

        mockMvc.perform(post("/api/v5/demandes/" + initialeId + "/prolongation-maternite")
                        .header("Authorization", "Bearer " + tokenDa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PROLONGATION_NON_AUTORISEE")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerCircuitEnBase() {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit Agent - Maternite");
        circuit.setTypeAbsenceCible(TypeAbsence.CONGE_MATERNITE);
        circuit.setActif(true);
        return circuitRepo.saveAndFlush(circuit).getId();
    }

    private UUID creerInitialeValidee(UUID circuitId) {
        DemandeCongeMaternite demande = new DemandeCongeMaternite();
        demande.setDemandeurIdentifiantExterne(DA_ID);
        demande.setUniteIdentifiantExterne("UNITE-MAT");
        demande.setType(TypeAbsence.CONGE_MATERNITE);
        demande.setDateDebut(LocalDate.of(2026, 3, 1));
        demande.setDateFin(LocalDate.of(2026, 5, 31));
        demande.setNombreJours(98);
        demande.setEstProlongation(false);
        demande.setCircuitId(circuitId);
        demande.setStatut(StatutDemande.VALIDEE);
        return demandeRepo.saveAndFlush(demande).getId();
    }

    private UUID creerInitialeEnValidationEtape() {
        DemandeCongeMaternite demande = new DemandeCongeMaternite();
        demande.setDemandeurIdentifiantExterne(DA_ID);
        demande.setUniteIdentifiantExterne("UNITE-MAT");
        demande.setType(TypeAbsence.CONGE_MATERNITE);
        demande.setDateDebut(LocalDate.of(2026, 4, 1));
        demande.setDateFin(LocalDate.of(2026, 6, 30));
        demande.setNombreJours(98);
        demande.setEstProlongation(false);
        demande.setCircuitId(null);
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
