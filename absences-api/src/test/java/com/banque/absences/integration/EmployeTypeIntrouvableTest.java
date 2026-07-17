package com.banque.absences.integration;

import com.banque.absences.testsupport.KeycloakAdminMock;
import com.banque.absences.security.KeycloakClaims;
import com.banque.absences.service.DoublonDetectionService;
import com.banque.absences.service.CircuitDeterminationService;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-ADM-006 — Aucun employé représentatif du grade déclenché.
 *
 * RÈGLE ABSOLUE vérifiée :
 *   422 EMPLOYE_TYPE_INTROUVABLE ≠ 409 DOUBLON_VALIDATEUR_DETECTE
 *   Le premier signifie que le contrôle n'a pas pu s'exécuter (données manquantes).
 *   Le second signifie que le contrôle s'est exécuté et a trouvé un doublon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmployeTypeIntrouvableTest {

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAdmin;

    @Autowired private MockMvc mockMvc;

    @MockBean private DoublonDetectionService     doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-adm006").generate();
        jwksMockServer     = new MockWebServer();
        adminApiMockServer = new MockWebServer();
        jwksMockServer.start();
        adminApiMockServer.start();

        final String jwksJson      = new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toString();
        final String tokenResponse = "{\"access_token\":\"fake-admin-token\","
                + "\"token_type\":\"Bearer\",\"expires_in\":3600}";

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

        // L'API admin retourne [] pour tout grade → EmployeTypeIntrouvableException
        // Realm sans aucun utilisateur : la recherche par grade renvoie une liste vide,
        // donc aucun employé-type représentatif de GRADE_FANTOME.
        adminApiMockServer.setDispatcher(KeycloakAdminMock.dispatcher(Map.of()));

        registry.add("keycloak.jwks-uri",
                () -> jwksMockServer.url("/realms/afb/protocol/openid-connect/certs").toString());
        registry.add("keycloak.admin-api-base-url",
                () -> adminApiMockServer.url("").toString());
    }

    @BeforeAll
    void initTokens() throws Exception {
        tokenAdmin = buildJwt("admin-rh-001", "ADMIN", List.of("ADMIN_RH"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ── Cas principal : 422 EMPLOYE_TYPE_INTROUVABLE ──────────────────────────

    @Test
    @DisplayName("US-ADM-006 — gradeDeclencheur=GRADE_FANTOME (liste vide) → 422 EMPLOYE_TYPE_INTROUVABLE")
    void gradeFantome_retourne422_employeTypeIntrouvable() throws Exception {
        mockMvc.perform(post("/api/v5/admin/circuits")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nom": "Circuit Fantome",
                                  "typeAbsenceCible": "CONGE_ANNUEL",
                                  "gradeDeclencheur": "GRADE_FANTOME",
                                  "etapesIntermediaires": [
                                    {
                                      "mecanismeResolution": "HIERARCHIQUE",
                                      "roleHabilite": "MANAGER"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code",             is("EMPLOYE_TYPE_INTROUVABLE")))
                .andExpect(jsonPath("$.gradeDeclencheur", is("GRADE_FANTOME")));
    }

    // ── Garde-fou : 422 ne doit PAS être 409 DOUBLON_VALIDATEUR_DETECTE ──────

    @Test
    @DisplayName("RÈGLE ABSOLUE — Le code renvoyé n'est pas 409 DOUBLON_VALIDATEUR_DETECTE")
    void gradeFantome_codeNestPasDoublonValidateur() throws Exception {
        mockMvc.perform(post("/api/v5/admin/circuits")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nom": "Circuit Fantome",
                                  "typeAbsenceCible": "CONGE_ANNUEL",
                                  "gradeDeclencheur": "GRADE_FANTOME",
                                  "etapesIntermediaires": [
                                    {
                                      "mecanismeResolution": "HIERARCHIQUE",
                                      "roleHabilite": "MANAGER"
                                    }
                                  ]
                                }
                                """))
                // Ne doit PAS être 409 Conflict
                .andExpect(status().isUnprocessableEntity())
                // Le code fonctionnel ne doit PAS être celui du doublon
                .andExpect(jsonPath("$.code", not("DOUBLON_VALIDATEUR_DETECTE")))
                .andExpect(jsonPath("$.code", not("DOUBLON_DETECTE")));
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
