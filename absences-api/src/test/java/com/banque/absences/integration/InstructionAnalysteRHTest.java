package com.banque.absences.integration;

import com.banque.absences.domain.DemandeCongeAnnuel;
import com.banque.absences.domain.DemandeMissionLongue;
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
 * US-CIR-010 — Instruction du dossier par l'Analyste RH avant transmission DRH.
 *
 * Cas 1 : CONGE_ANNUEL sans justificatif -> 200, EN_VALIDATION_DRH
 *         (CONGE_ANNUEL n'exige pas de justificatif)
 * Cas 2 : MISSION_LONGUE sans justificatif -> 409 JUSTIFICATIF_REQUIS
 * Cas 3 : MISSION_LONGUE avec justificatif depose -> 200, EN_VALIDATION_DRH
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstructionAnalysteRHTest {

    private static final String ANALYSTE_ID = "analyste-rh-cir010-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAnalyste;

    @Autowired private MockMvc                        mockMvc;
    @Autowired private DemandeAbsenceRepository       demandeRepo;
    @Autowired private JustificatifDocumentRepository justificatifRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-cir010").generate();
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

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 1 — CONGE_ANNUEL sans justificatif -> 200 EN_VALIDATION_DRH
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 1 — CONGE_ANNUEL sans justificatif : instruction autorisee -> EN_VALIDATION_DRH")
    void cas1_congeAnnuel_sansJustificatif_transmis() throws Exception {
        UUID demandeId = creerDemandeEnInstruction(TypeAbsence.CONGE_ANNUEL);

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/instruction")
                        .header("Authorization", "Bearer " + tokenAnalyste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_DRH")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 2 — MISSION_LONGUE sans justificatif -> 409 JUSTIFICATIF_REQUIS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 2 — MISSION_LONGUE sans justificatif : instruction bloquee -> 409 JUSTIFICATIF_REQUIS")
    void cas2_missionLongue_sansJustificatif_409() throws Exception {
        UUID demandeId = creerDemandeEnInstruction(TypeAbsence.MISSION_LONGUE);

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/instruction")
                        .header("Authorization", "Bearer " + tokenAnalyste))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("JUSTIFICATIF_REQUIS")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 3 — MISSION_LONGUE avec justificatif depose -> 200 EN_VALIDATION_DRH
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 3 — MISSION_LONGUE avec justificatif : instruction autorisee -> EN_VALIDATION_DRH")
    void cas3_missionLongue_avecJustificatif_transmis() throws Exception {
        UUID demandeId = creerDemandeEnInstruction(TypeAbsence.MISSION_LONGUE);
        deposerJustificatif(demandeId);

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/instruction")
                        .header("Authorization", "Bearer " + tokenAnalyste))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_DRH")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID creerDemandeEnInstruction(TypeAbsence type) {
        if (type == TypeAbsence.MISSION_LONGUE) {
            DemandeMissionLongue demande = new DemandeMissionLongue();
            demande.setDemandeurIdentifiantExterne("da-cir010-001");
            demande.setUniteIdentifiantExterne("UNITE-CIR010");
            demande.setType(TypeAbsence.MISSION_LONGUE);
            demande.setDateDebut(LocalDate.of(2026, 9, 1));
            demande.setDateFin(LocalDate.of(2026, 9, 30));
            demande.setNombreJours(20);
            demande.setStatut(StatutDemande.EN_INSTRUCTION_ANALYSTE_RH);
            demande.setCircuitNom("Circuit Reseau - Mission Longue");
            return demandeRepo.saveAndFlush(demande).getId();
        } else {
            DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
            demande.setDemandeurIdentifiantExterne("da-cir010-002");
            demande.setUniteIdentifiantExterne("UNITE-CIR010");
            demande.setType(TypeAbsence.CONGE_ANNUEL);
            demande.setDateDebut(LocalDate.of(2026, 8, 3));
            demande.setDateFin(LocalDate.of(2026, 8, 14));
            demande.setNombreJours(8);
            demande.setStatut(StatutDemande.EN_INSTRUCTION_ANALYSTE_RH);
            demande.setCircuitNom("Circuit Agent - Conge Annuel");
            return demandeRepo.saveAndFlush(demande).getId();
        }
    }

    private void deposerJustificatif(UUID demandeId) {
        JustificatifDocument doc = new JustificatifDocument();
        doc.setDemandeId(demandeId);
        doc.setTypePiece("ORDRE_DE_MISSION");
        doc.setUrlFichier("https://storage.banque.com/docs/" + demandeId + "/ordre_mission.pdf");
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
