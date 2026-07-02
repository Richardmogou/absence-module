package com.banque.absences.integration;

import com.banque.absences.domain.DemandeCongeAnnuel;
import com.banque.absences.domain.SoldeConge;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.SoldeCongeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-GES-004 — Retour anticipé.
 *
 * Scénario :
 *   Demande CONGE_ANNUEL du 03/08 au 14/08 (10 jours), retour effectif le 10/08.
 *   Jours non consommés = ChronoUnit.DAYS.between(10/08, 14/08) = 4.
 *   Le solde jours_pris doit baisser de 10 à 6 (recrédit de 4 jours).
 *   Le statut de la demande doit passer à CLOTUREE.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetourAnticipeTest {

    private static final String AGENT_ID = "agent-retour-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAgent;

    @Autowired private MockMvc                  mockMvc;
    @Autowired private DemandeAbsenceRepository demandeRepo;
    @Autowired private SoldeCongeRepository     soldeRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-retour001").generate();
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
    // Scénario principal : 10 jours consommés, retour le 10/08 -> recrédit 4 j
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Retour anticipé CONGE_ANNUEL le 10/08 sur période 03/08-14/08 : recrédit 4 jours")
    void retourAnticipe_congeAnnuel_recreditQuatreJours() throws Exception {
        int exercice = LocalDate.now().getYear();

        // ── Solde initial : 10 jours pris ────────────────────────────────────
        SoldeConge solde = new SoldeConge();
        solde.setEmployeIdentifiantExterne(AGENT_ID);
        solde.setExercice(exercice);
        solde.setJoursAcquis(30);
        solde.setJoursPris(10);
        solde.setJoursRestants(20);
        SoldeConge soldeSauve = soldeRepo.saveAndFlush(solde);
        int joursPrisAvant = soldeSauve.getJoursPris();

        // ── Demande CONGE_ANNUEL du 03/08 au 14/08 au statut VALIDEE ─────────
        UUID demandeId = creerDemandeValidee();

        // ── Retour anticipé le 10/08 ─────────────────────────────────────────
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/retour-anticipe")
                        .header("Authorization", "Bearer " + tokenAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateRetourEffective\": \"2026-08-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("CLOTUREE")));

        // ── Vérification du recrédit ──────────────────────────────────────────
        SoldeConge soldeApres = soldeRepo.findByEmployeIdentifiantExterneAndExercice(
                AGENT_ID, exercice).orElseThrow();
        int joursPrisApres = soldeApres.getJoursPris();

        assertThat(joursPrisAvant - joursPrisApres)
                .as("Le recrédit doit être exactement 4 jours (entre 10/08 et 14/08)")
                .isEqualTo(4);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerDemandeValidee() {
        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        demande.setDemandeurIdentifiantExterne(AGENT_ID);
        demande.setUniteIdentifiantExterne("UNITE-RETOUR-001");
        demande.setType(TypeAbsence.CONGE_ANNUEL);
        demande.setDateDebut(LocalDate.of(2026, 8, 3));
        demande.setDateFin(LocalDate.of(2026, 8, 14));
        demande.setNombreJours(10);
        demande.setStatut(StatutDemande.VALIDEE);
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
