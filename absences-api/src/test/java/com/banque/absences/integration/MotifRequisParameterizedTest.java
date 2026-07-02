package com.banque.absences.integration;

import com.banque.absences.domain.DemandeCongeAnnuel;
import com.banque.absences.domain.StatutDemande;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * US-CIR-013 — La contrainte MOTIF_REQUIS est appliquée aux 3 étapes intermédiaires
 * du Circuit Agent (Back-up pos.0, Manager pos.1, Chef de processus pos.2).
 *
 * Le check MOTIF_REQUIS intervient avant toute vérification d'habilitation dans
 * AbsenceServiceImpl.enregistrerDecisionEtape, ce qui permet de tester les 3 positions
 * avec un seul token valide et sans snapshot d'étape.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MotifRequisParameterizedTest {

    private static final String VALIDATEUR_ID = "validateur-motif-req-001";
    private static final String UNITE         = "UNITE-MOTIF-REQ-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey         rsaKey;

    private String tokenValidateur;

    @Autowired private MockMvc                  mockMvc;
    @Autowired private DemandeAbsenceRepository demandeRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-motif-req").generate();
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
        tokenValidateur = buildJwt(VALIDATEUR_ID, "AGENT", List.of("EMPLOYE"));
        when(doublonService.detecterDoublon(any())).thenReturn(false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    /**
     * Aux positions 0 (Back-up), 1 (Manager) et 2 (Chef de processus), un REJETER
     * sans motif doit retourner 422 MOTIF_REQUIS avant toute vérification d'habilitation.
     */
    @ParameterizedTest(name = "Position {0} — REJETER sans motif → 422 MOTIF_REQUIS")
    @ValueSource(ints = {0, 1, 2})
    void rejeterSansMotifRetourne422(int position) throws Exception {
        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        demande.setDemandeurIdentifiantExterne(VALIDATEUR_ID);
        demande.setUniteIdentifiantExterne(UNITE);
        demande.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
        demande.setPositionEtapeCourante(position);
        demande.setDateDebut(LocalDate.of(2026, 8, 3));
        demande.setDateFin(LocalDate.of(2026, 8, 14));
        demande.setNombreJours(8);
        demande = (DemandeCongeAnnuel) demandeRepo.saveAndFlush(demande);

        mockMvc.perform(post("/api/v5/demandes/" + demande.getId() + "/validation")
                        .header("Authorization", "Bearer " + tokenValidateur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJETER\",\"motif\":null}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("MOTIF_REQUIS")));
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
