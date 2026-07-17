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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CDCT EX-9 / décision E (§4.1.7-E) — Override PATCH /{id}/statut encadré.
 *
 * L'override contourne la machine à états et tous les effets de bord (solde, document).
 * Le CDCT impose trois garde-fous, un par test ici :
 *   1. motif obligatoire            -> 400 VALIDATION_ERREUR
 *   2. cible VALIDEE interdite      -> 422 OVERRIDE_CIBLE_INTERDITE
 *   3. journalisation qui/quand/ancien→nouveau/motif -> ligne dans audit_override_statut
 *
 * Le 4e test verrouille le point le plus facile à casser par régression : un forçage refusé
 * ne doit RIEN écrire, ni statut ni journal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OverrideStatutAdminRHTest {

    private static final String ADMIN_RH_ID = "admin-rh-ex9-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAdminRh;

    @Autowired private MockMvc                  mockMvc;
    @Autowired private DemandeAbsenceRepository demandeRepo;
    @Autowired private JdbcTemplate             jdbcTemplate;

    @MockBean private DoublonDetectionService     doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-ex9").generate();
        jwksMockServer     = new MockWebServer();
        adminApiMockServer = new MockWebServer();
        jwksMockServer.start();
        adminApiMockServer.start();

        final String jwksJson = new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toString();

        jwksMockServer.setDispatcher(new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                String path = req.getPath() == null ? "" : req.getPath();
                if (path.contains("/jwks") || path.contains("/certs"))
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json").setBody(jwksJson);
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
        tokenAdminRh = buildJwt(ADMIN_RH_ID, "Administrateur RH", List.of("EMPLOYE", "ADMIN_RH"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    @Test
    @DisplayName("EX-9 (1) — forçage sans motif : 400 VALIDATION_ERREUR")
    void override_sansMotif_refuse() throws Exception {
        UUID demandeId = creerDemande(StatutDemande.EN_VALIDATION_DRH);

        mockMvc.perform(patch("/api/v5/demandes/" + demandeId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdminRh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"REJETEE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERREUR")));
    }

    @Test
    @DisplayName("EX-9 (1bis) — motif blanc : refusé aussi (@NotBlank, pas @NotNull)")
    void override_motifBlanc_refuse() throws Exception {
        UUID demandeId = creerDemande(StatutDemande.EN_VALIDATION_DRH);

        mockMvc.perform(patch("/api/v5/demandes/" + demandeId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdminRh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"REJETEE\",\"motif\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERREUR")));
    }

    @Test
    @DisplayName("EX-9 (2) — cible VALIDEE interdite : 422 OVERRIDE_CIBLE_INTERDITE")
    void override_versValidee_refuse() throws Exception {
        UUID demandeId = creerDemande(StatutDemande.EN_VALIDATION_DRH);

        mockMvc.perform(patch("/api/v5/demandes/" + demandeId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdminRh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"VALIDEE\",\"motif\":\"tentative de forcage\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("OVERRIDE_CIBLE_INTERDITE")));
    }

    @Test
    @DisplayName("EX-9 (2bis) — un forçage refusé n'écrit ni statut ni journal")
    void override_refuse_neLaisseAucuneTrace() throws Exception {
        UUID demandeId = creerDemande(StatutDemande.EN_VALIDATION_DRH);

        mockMvc.perform(patch("/api/v5/demandes/" + demandeId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdminRh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"VALIDEE\",\"motif\":\"tentative de forcage\"}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(demandeRepo.findById(demandeId).orElseThrow().getStatut())
                .isEqualTo(StatutDemande.EN_VALIDATION_DRH);
        assertThat(compterLignesAudit(demandeId)).isZero();
    }

    @Test
    @DisplayName("EX-9 (3) — forçage légitime : 200 + journal qui/quand/ancien→nouveau/motif")
    void override_legitime_journalise() throws Exception {
        UUID demandeId = creerDemande(StatutDemande.EN_VALIDATION_DRH);
        String motif = "Demande saisie en double, forcage apres accord DRH";

        mockMvc.perform(patch("/api/v5/demandes/" + demandeId + "/statut")
                        .header("Authorization", "Bearer " + tokenAdminRh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"REJETEE\",\"motif\":\"" + motif + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("REJETEE")));

        Map<String, Object> ligne = jdbcTemplate.queryForMap(
                "SELECT auteur_identifiant_externe, statut_ancien, statut_nouveau, motif, date_action "
                + "FROM audit_override_statut WHERE demande_id = ?", demandeId);

        assertThat(ligne.get("auteur_identifiant_externe")).isEqualTo(ADMIN_RH_ID);
        assertThat(ligne.get("statut_ancien")).isEqualTo("EN_VALIDATION_DRH");
        assertThat(ligne.get("statut_nouveau")).isEqualTo("REJETEE");
        assertThat(ligne.get("motif")).isEqualTo(motif);
        assertThat(ligne.get("date_action")).isNotNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerDemande(StatutDemande statut) {
        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        demande.setDemandeurIdentifiantExterne("da-ex9-" + UUID.randomUUID());
        demande.setUniteIdentifiantExterne("UNITE-EX9");
        demande.setType(TypeAbsence.CONGE_ANNUEL);
        demande.setDateDebut(LocalDate.of(2026, 11, 3));
        demande.setDateFin(LocalDate.of(2026, 11, 14));
        demande.setNombreJours(8);
        demande.setStatut(statut);
        demande.setCircuitNom("Circuit Agent - Conge Annuel");
        return demandeRepo.saveAndFlush(demande).getId();
    }

    private Long compterLignesAudit(UUID demandeId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_override_statut WHERE demande_id = ?",
                Long.class, demandeId);
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
