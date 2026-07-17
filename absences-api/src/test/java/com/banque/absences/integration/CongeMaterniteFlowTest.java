package com.banque.absences.integration;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.DemandeCongeMaternite;
import com.banque.absences.domain.JustificatifDocument;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.JustificatifDocumentRepository;
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
import org.springframework.test.web.servlet.MvcResult;

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
 * Parcours congé maternité : la date de début n'est PAS saisie à la création
 * (l'employé ne fournit qu'un certificat), c'est l'analyste RH qui la fixe à
 * l'instruction, après quoi le système calcule fin (+14 semaines) et 98 jours.
 *
 * Cas 1 : création sans date -> 201, dates nulles en base
 * Cas 2 : instruction sans date -> 400 (date obligatoire pour la maternité)
 * Cas 3 : instruction avec date -> 200 EN_VALIDATION_DRH + dates calculées automatiquement
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CongeMaterniteFlowTest {

    private static final String ANALYSTE_ID = "analyste-mat-001";
    private static final String EMPLOYE_ID  = "employe-mat-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenAnalyste;
    private String tokenEmploye;

    @Autowired private MockMvc                        mockMvc;
    @Autowired private DemandeAbsenceRepository       demandeRepo;
    @Autowired private JustificatifDocumentRepository justificatifRepo;
    @Autowired private ObjectMapper                   objectMapper;

    @MockBean private DoublonDetectionService     doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-mat").generate();
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
        tokenAnalyste = buildJwt(ANALYSTE_ID, "AGENT", List.of("EMPLOYE", "ANALYSTE_RH"));
        tokenEmploye  = buildJwt(EMPLOYE_ID, "AGENT", List.of("EMPLOYE"));
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ── Cas 1 — création sans date -> dates nulles ──────────────────────────────
    @Test
    @DisplayName("Cas 1 — création maternité sans date : 201 et dates nulles en base")
    void cas1_creation_sansDate_datesNulles() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenEmploye)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONGE_MATERNITE\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        DemandeAbsence d = demandeRepo.findById(id).orElseThrow();

        assertThat(d.getType()).isEqualTo(TypeAbsence.CONGE_MATERNITE);
        assertThat(d.getDateDebut()).isNull();
        assertThat(d.getDateFin()).isNull();
        assertThat(d.getNombreJours()).isNull();
    }

    // ── Cas 2 — instruction sans date -> 400 ────────────────────────────────────
    @Test
    @DisplayName("Cas 2 — instruction maternité sans date : 400 REQUETE_INVALIDE")
    void cas2_instruction_sansDate_400() throws Exception {
        UUID id = creerMaterniteEnInstruction();

        mockMvc.perform(post("/api/v5/demandes/" + id + "/instruction")
                        .header("Authorization", "Bearer " + tokenAnalyste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("REQUETE_INVALIDE")));
    }

    // ── Cas 3 — instruction avec date -> 200 + dates calculées ──────────────────
    @Test
    @DisplayName("Cas 3 — instruction maternité avec date : EN_VALIDATION_DRH + fin=+14sem, 98 jours")
    void cas3_instruction_avecDate_calculeDates() throws Exception {
        UUID id = creerMaterniteEnInstruction();
        LocalDate dateDebut = LocalDate.of(2026, 8, 1);

        mockMvc.perform(post("/api/v5/demandes/" + id + "/instruction")
                        .header("Authorization", "Bearer " + tokenAnalyste)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateDebut\":\"2026-08-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_DRH")));

        DemandeAbsence d = demandeRepo.findById(id).orElseThrow();
        assertThat(d.getDateDebut()).isEqualTo(dateDebut);
        assertThat(d.getDateFin()).isEqualTo(dateDebut.plusWeeks(14));
        assertThat(d.getNombreJours()).isEqualTo(98);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Congé maternité SANS dates, au statut instruction, avec certificat déposé. */
    private UUID creerMaterniteEnInstruction() {
        DemandeCongeMaternite demande = new DemandeCongeMaternite();
        demande.setDemandeurIdentifiantExterne("da-mat-001");
        demande.setUniteIdentifiantExterne("UNITE-MAT");
        demande.setType(TypeAbsence.CONGE_MATERNITE);
        demande.setEstProlongation(false);
        demande.setStatut(StatutDemande.EN_INSTRUCTION_ANALYSTE_RH);
        demande.setCircuitNom("Circuit Agent - Conge Maternite");
        UUID id = demandeRepo.saveAndFlush(demande).getId();

        JustificatifDocument doc = new JustificatifDocument();
        doc.setDemandeId(id);
        doc.setTypePiece("CERTIFICAT_GROSSESSE");
        doc.setUrlFichier("https://storage.banque.com/docs/" + id + "/certificat.pdf");
        doc.setDeposeLe(Instant.now());
        justificatifRepo.saveAndFlush(doc);

        return id;
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
