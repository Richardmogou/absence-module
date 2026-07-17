package com.banque.absences.integration;

import com.banque.absences.testsupport.KeycloakAdminMock;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import com.banque.absences.security.KeycloakClaims;
import com.banque.absences.service.CircuitDeterminationService;
import com.banque.absences.service.DoublonDetectionService;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-ADM-004 / US-ADM-005 — Scénario Directeur complet (DA-ABSENCES-v5.0).
 *
 * Étape 1 : POST /api/v5/admin/circuits avec HIERARCHIQUE(N1) + ROLE_FIXE_GLOBAL(DG)
 *           → 409 DOUBLON_VALIDATEUR_DETECTE
 * Étape 2 : POST /api/v5/admin/circuits/{id}/resolution-doublon {"choix":"SUPPRIMER"}
 *           → 200, estModeleNomme=true
 * Étape 3 : vérification en base : 4 étapes, sans la ROLE_FIXE_GLOBAL DG supprimée
 *
 * Chaîne Keycloak mockée :
 *   resoudreEmployeTypeParGrade("DIRECTEUR") → ["id-paul-ateba"]
 *   resoudreHierarchique("id-paul-ateba", 1) → "id-jean-mbarga"
 *   resoudreGradeParIdentifiant("id-jean-mbarga") → "DG"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DoublonValidateurDirecteurTest {

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey        rsaKey;

    private String tokenAdmin;

    @Autowired private MockMvc                      mockMvc;
    @Autowired private EtapeModeleCircuitRepository etapeRepo;
    @Autowired private ObjectMapper                 objectMapper;

    @MockBean private DoublonDetectionService     doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-adm004").generate();
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

        // Chaîne hiérarchique : DIRECTEUR → id-paul-ateba → N+1 id-jean-mbarga → grade DG
        // Employé-type du grade DIRECTEUR, dont le N+1 est un DG : le doublon attendu.
        adminApiMockServer.setDispatcher(KeycloakAdminMock.dispatcher(Map.of(
                "id-paul-ateba",  KeycloakAdminMock.utilisateur().grade("DIRECTEUR").manager("id-jean-mbarga"),
                "id-jean-mbarga", KeycloakAdminMock.utilisateur().grade("DG"))));

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

    // ── Étape 1 : POST → 409 DOUBLON_VALIDATEUR_DETECTE ─────────────────────

    @Test
    @DisplayName("DA-ABSENCES-v5.0 Directeur — Étape 1 : création → 409 DOUBLON_VALIDATEUR_DETECTE avec choixPossibles")
    void etape1_creationDirecteur_retourne409DoublonValidateur() throws Exception {
        mockMvc.perform(post("/api/v5/admin/circuits")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nom": "Directeur",
                                  "typeAbsenceCible": "CONGE_ANNUEL",
                                  "gradeDeclencheur": "DIRECTEUR",
                                  "etapesIntermediaires": [
                                    {"mecanismeResolution": "HIERARCHIQUE",    "roleHabilite": "N1"},
                                    {"mecanismeResolution": "ROLE_FIXE_GLOBAL","roleHabilite": "DG"}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code",                      is("DOUBLON_VALIDATEUR_DETECTE")))
                .andExpect(jsonPath("$.circuitId").isNotEmpty())
                .andExpect(jsonPath("$.etapeHierarchiqueId").isNotEmpty())
                .andExpect(jsonPath("$.etapeRoleFixeRedondanteId").isNotEmpty())
                .andExpect(jsonPath("$.choixPossibles", hasItems("SUPPRIMER", "CONSERVER")));
    }

    // ── Étapes 2 + 3 : résolution SUPPRIMER + vérification en base ───────────

    @Test
    @DisplayName("DA-ABSENCES-v5.0 Directeur — Étapes 2+3 : résolution SUPPRIMER → 200 estModeleNomme=true, 4 étapes en base sans ROLE_FIXE DG")
    void etape2et3_resolutionSupprimer_circuitValide() throws Exception {
        // 1. Créer le circuit → 409 (on capture la réponse pour extraire les IDs)
        MvcResult postResult = mockMvc.perform(post("/api/v5/admin/circuits")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nom": "Directeur v2",
                                  "typeAbsenceCible": "CONGE_ANNUEL",
                                  "gradeDeclencheur": "DIRECTEUR",
                                  "etapesIntermediaires": [
                                    {"mecanismeResolution": "HIERARCHIQUE",    "roleHabilite": "N1"},
                                    {"mecanismeResolution": "ROLE_FIXE_GLOBAL","roleHabilite": "DG"}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode body = objectMapper.readTree(postResult.getResponse().getContentAsString());
        UUID circuitId                = UUID.fromString(body.get("circuitId").asText());
        UUID etapeRoleFixeRedondanteId = UUID.fromString(body.get("etapeRoleFixeRedondanteId").asText());

        // 2. Résoudre avec SUPPRIMER → 200, estModeleNomme=true
        mockMvc.perform(post("/api/v5/admin/circuits/" + circuitId + "/resolution-doublon")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "choix": "SUPPRIMER",
                                  "etapeId": "%s",
                                  "gradeDeclencheur": "DIRECTEUR"
                                }
                                """.formatted(etapeRoleFixeRedondanteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estModeleNomme", is(true)));

        // 3. Vérification en base : seule l'étape N1-HIERARCHIQUE subsiste.
        //    Le circuit n'a jamais porté d'étapes ANALYSTE_RH/DRH : la création ne pose que
        //    les étapes intermédiaires du payload (elles viennent de V15 pour les circuits
        //    standard). Restent donc les 2 étapes demandées, moins la ROLE_FIXE_GLOBAL DG
        //    supprimée par la résolution.
        var etapes = etapeRepo.findByModeleCircuitIdOrderByOrdreAsc(circuitId);
        assertThat(etapes).hasSize(1);

        // L'étape ROLE_FIXE DG supprimée n'existe plus en base
        boolean dgRoleFixePresentEnBase = etapes.stream()
                .anyMatch(e -> "DG".equals(e.getLibelle()) &&
                        e.getRegles().stream().anyMatch(r ->
                                r.getMecanisme() ==
                                com.banque.absences.domain.MecanismeResolution.ROLE_FIXE_GLOBAL));
        assertThat(dgRoleFixePresentEnBase)
                .as("L'étape ROLE_FIXE_GLOBAL DG ne doit plus exister en base après SUPPRIMER")
                .isFalse();

        // L'étape hiérarchique N1, elle, est conservée : SUPPRIMER ne retire que la redondante.
        List<String> libelles = etapes.stream()
                .map(com.banque.absences.domain.EtapeModeleCircuit::getLibelle)
                .toList();
        assertThat(libelles).containsExactly("N1");
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
