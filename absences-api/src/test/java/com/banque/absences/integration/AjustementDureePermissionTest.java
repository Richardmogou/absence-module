package com.banque.absences.integration;

import com.banque.absences.domain.DemandePermission;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-PERM-003 — Confirmation/ajustement de la durée d'une Permission motif AUTRE_MOTIF par la DRH.
 *
 * Scénario :
 *   Permission AUTRE_MOTIF créée avec nombreJours=3, en EN_VALIDATION_DRH.
 *   POST /validation-drh {"decision":"VALIDER","nombreJoursAjuste":2}
 *   -> 200, body.nombreJours=2 (durée ajustée par la DRH).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AjustementDureePermissionTest {

    private static final String DRH_ID = "drh-perm003-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenDrh;

    @Autowired private MockMvc                        mockMvc;
    @Autowired private DemandeAbsenceRepository       demandeRepo;
    @Autowired private JustificatifDocumentRepository justificatifRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-perm003").generate();
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
    @DisplayName("US-PERM-003 — Permission AUTRE_MOTIF : DRH ajuste nombreJours 3 -> 2")
    void drhAjusteDuree_permissionAutreMotif() throws Exception {
        // Permission AUTRE_MOTIF avec nombreJours=3, déjà instruite et avec justificatif
        UUID demandeId = creerPermissionAutreMotifEnValidationDRH(3);
        deposerJustificatif(demandeId);

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation-drh")
                        .header("Authorization", "Bearer " + tokenDrh)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "VALIDER",
                                  "nombreJoursAjuste": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("VALIDEE")))
                .andExpect(jsonPath("$.nombreJours", is(2)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerPermissionAutreMotifEnValidationDRH(int nombreJours) {
        DemandePermission demande = new DemandePermission();
        demande.setDemandeurIdentifiantExterne("da-perm003-001");
        demande.setUniteIdentifiantExterne("UNITE-PERM003");
        demande.setType(TypeAbsence.PERMISSION);
        demande.setDateDebut(LocalDate.of(2026, 12, 1));
        demande.setDateFin(LocalDate.of(2026, 12, 3));
        demande.setNombreJours(nombreJours);
        demande.setCodeMotif("AUTRE_MOTIF");
        demande.setStatut(StatutDemande.EN_VALIDATION_DRH);
        demande.setCircuitNom("Circuit Agent - Permission");
        return demandeRepo.saveAndFlush(demande).getId();
    }

    private void deposerJustificatif(UUID demandeId) {
        JustificatifDocument doc = new JustificatifDocument();
        doc.setDemandeId(demandeId);
        doc.setTypePiece("Justificatif autre motif");
        doc.setUrlFichier("https://storage.banque.com/docs/" + demandeId + "/justif.pdf");
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
