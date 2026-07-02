package com.banque.absences.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * US-SEC-002 — Vérifie que tout JWT expiré est rejeté avec 401 JETON_INVALIDE,
 * quelle que soit la route appelée, sans jamais franchir le filtre de sécurité.
 *
 * Stratégie :
 *  - Génération d'une paire RSA éphémère en mémoire (KeyId = "test-key")
 *  - MockWebServer expose le JWKS correspondant (clé publique uniquement)
 *  - L'URL du JWKS est injectée via @DynamicPropertySource avant le démarrage du contexte
 *  - Le JWT est signé avec la clé privée et contient exp = now - 3600 s
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JetonExpireIntegrationTest {

    // ── Infrastructure partagée ───────────────────────────────────────────────

    private static MockWebServer jwksMockServer;
    private static RSAKey         rsaKey;
    private static String         expiredJwt;

    @Autowired
    private MockMvc mockMvc;

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    @BeforeAll
    static void setUp() throws Exception {
        // 1. Générer la paire RSA de test (2048 bits, kid=test-key)
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("test-key")
                .generate();

        // 2. Construire le JWT expiré signé avec la clé privée
        expiredJwt = buildExpiredJwt(rsaKey);

        // 3. Démarrer MockWebServer qui sert le JWKS (clé publique)
        jwksMockServer = new MockWebServer();
        jwksMockServer.start();
        enqueueJwksResponse();
    }

    @AfterAll
    static void tearDown() throws Exception {
        jwksMockServer.close();
    }

    /**
     * Injecte l'URL du JWKS mock avant que Spring construise le contexte,
     * ce qui permet à SecurityConfig d'utiliser la vraie validation de signature
     * tout en pointant sur notre clé de test.
     */
    @DynamicPropertySource
    static void jwksUri(DynamicPropertyRegistry registry) throws Exception {
        // Le serveur doit être démarré ici — on force l'init statique
        if (jwksMockServer == null) {
            rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
            expiredJwt = buildExpiredJwt(rsaKey);
            jwksMockServer = new MockWebServer();
            jwksMockServer.start();
        }
        enqueueJwksResponse();
        registry.add("keycloak.jwks-uri",
                () -> jwksMockServer.url("/jwks").toString());
    }

    // ── Source de données paramétrée ──────────────────────────────────────────

    static Stream<Object[]> endpoints() {
        return Stream.of(
                new Object[]{"GET",  "/api/v5/demandes/1/preview",    null},
                new Object[]{"POST", "/api/v5/admin/circuits",         "{}"},
                new Object[]{"POST", "/api/v5/demandes/1/validation",  "{}"}
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} {1} avec jeton expiré → 401 JETON_INVALIDE")
    @MethodSource("endpoints")
    @DisplayName("US-SEC-002 — Jeton expiré rejeté sur tous les endpoints protégés")
    void jetonExpireRetourne401(String method, String uri, String body) throws Exception {
        // Chaque test a besoin d'une réponse JWKS fraîche dans la file
        enqueueJwksResponse();

        var requestBuilder = "GET".equals(method)
                ? get(uri)
                : post(uri).contentType(MediaType.APPLICATION_JSON).content(body);

        mockMvc.perform(requestBuilder
                        .header("Authorization", "Bearer " + expiredJwt)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code",    is("JETON_INVALIDE")))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private static String buildExpiredJwt(RSAKey key) throws Exception {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("employe-test-42")
                .issuer("https://keycloak.banque.com/realms/afb")
                .audience("absences-api")
                .issueTime(Date.from(now.minusSeconds(7200)))   // émis il y a 2 h
                .expirationTime(Date.from(now.minusSeconds(3600))) // expiré il y a 1 h
                .claim(KeycloakClaims.REALM_ACCESS,
                        Map.of(KeycloakClaims.ROLES, java.util.List.of("EMPLOYE")))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static void enqueueJwksResponse() throws Exception {
        // Exposer uniquement la clé publique dans le JWKS
        String jwksJson = new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK())
                .toString();

        jwksMockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(jwksJson)
        );
    }
}
