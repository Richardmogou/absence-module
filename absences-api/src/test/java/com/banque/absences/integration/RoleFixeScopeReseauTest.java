package com.banque.absences.integration;

import com.banque.absences.testsupport.KeycloakAdminMock;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.domain.DemandeCongeAnnuel;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import com.banque.absences.repository.ModeleCircuitRepository;
import com.banque.absences.repository.RegleAffectationRepository;
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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-CIR-008 / US-CIR-009 — Habilitation ROLE_FIXE_SCOPE_RESEAU.
 *
 * Verifie les 3 cas :
 *   Cas 1 : DR du Reseau A valide une demande d'un DA du Reseau A -> 200
 *   Cas 2 : DR du Reseau A tente de valider un DA du Reseau B -> 403 VALIDATEUR_NON_AUTORISE
 *   Cas 3 : DA sans CLAIM_RESEAU renseigne soumet et tente de valider -> 422 RESEAU_NON_RENSEIGNE
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleFixeScopeReseauTest {

    // Demandeurs
    private static final String DA_RESEAU_A_ID  = "da-cir008-reseau-a";
    private static final String DA_RESEAU_B_ID  = "da-cir008-reseau-b";
    private static final String DA_SANS_RESEAU_ID = "da-cir008-sans-reseau";

    // Validateur DR
    private static final String DR_RESEAU_A_ID  = "dr-cir008-reseau-a";

    private static final String RESEAU_A = "reseau-cir008-alpha";
    private static final String RESEAU_B = "reseau-cir008-beta";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    // Tokens
    private String tokenDaReseauA;
    private String tokenDaReseauB;
    private String tokenDaSansReseau;
    private String tokenDrReseauA;

    @Autowired private MockMvc                        mockMvc;
    @Autowired private ObjectMapper                   mapper;
    @Autowired private DemandeAbsenceRepository       demandeRepo;
    @Autowired private EtapeDemandeSnapshotRepository snapshotRepo;
    @Autowired private ModeleCircuitRepository        circuitRepo;
    @Autowired private EtapeModeleCircuitRepository   etapeModeleRepo;
    @Autowired private RegleAffectationRepository     regleRepo;

    @MockBean private DoublonDetectionService    doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey             = new RSAKeyGenerator(2048).keyID("test-key-cir008").generate();
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

        // API admin : le réseau d'un demandeur tiers se lit dans l'attribut `reseau` de sa
        // UserRepresentation (GET /users/{id}) — il n'y a pas de sous-ressource /reseau.
        // DA_SANS_RESEAU_ID n'est volontairement pas déclaré : 404 → Optional.empty()
        // → ReseauNonRenseigneException, le cas 3.
        adminApiMockServer.setDispatcher(KeycloakAdminMock.dispatcher(Map.of(
                DA_RESEAU_A_ID, KeycloakAdminMock.utilisateur().grade("DA").reseau(RESEAU_A),
                DA_RESEAU_B_ID, KeycloakAdminMock.utilisateur().grade("DA").reseau(RESEAU_B),
                DR_RESEAU_A_ID, KeycloakAdminMock.utilisateur().grade("DR").reseau(RESEAU_A))));

        registry.add("keycloak.jwks-uri",
                () -> jwksMockServer.url("/realms/afb/protocol/openid-connect/certs").toString());
        registry.add("keycloak.admin-api-base-url",
                () -> adminApiMockServer.url("").toString());
    }

    @BeforeAll
    void initTokensEtMocks() throws Exception {
        tokenDaReseauA   = buildJwt(DA_RESEAU_A_ID,   "DA", RESEAU_A, List.of("EMPLOYE"));
        tokenDaReseauB   = buildJwt(DA_RESEAU_B_ID,   "DA", RESEAU_B, List.of("EMPLOYE"));
        tokenDaSansReseau= buildJwt(DA_SANS_RESEAU_ID, "DA", null,     List.of("EMPLOYE"));
        tokenDrReseauA   = buildJwt(DR_RESEAU_A_ID,   "DR", RESEAU_A, List.of("EMPLOYE", "DR"));
        when(doublonService.detecterDoublon(any())).thenReturn(false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 1 — DR Reseau A valide un DA du Reseau A -> 200
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 1 — DR Reseau A + DA Reseau A : validation autorisee -> 200")
    void cas1_drReseauA_valide_daReseauA() throws Exception {
        UUID demandeId = creerDemandeEnValidation(DA_RESEAU_A_ID, RESEAU_A);

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDrReseauA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 2 — DR Reseau A tente un DA du Reseau B -> 403 VALIDATEUR_NON_AUTORISE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 2 — DR Reseau A + DA Reseau B : validation refusee -> 403 VALIDATEUR_NON_AUTORISE")
    void cas2_drReseauA_refuse_daReseauB() throws Exception {
        UUID demandeId = creerDemandeEnValidation(DA_RESEAU_B_ID, RESEAU_B);

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDrReseauA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("VALIDATEUR_NON_AUTORISE")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cas 3 — DA sans CLAIM_RESEAU -> 422 RESEAU_NON_RENSEIGNE
    // Le demandeur est l'appelant courant (sub = demandeurId) : resoudreReseauDemandeur
    // lit directement son JWT et trouve Optional.empty() -> ReseauNonRenseigneException.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cas 3 — DA sans CLAIM_RESEAU : demande en validation -> 422 RESEAU_NON_RENSEIGNE")
    void cas3_daSansReseau_422ReseauNonRenseigne() throws Exception {
        // Le demandeur est aussi l'appelant qui appelle /validation (sub identique).
        // On place la demande directement en base avec statut EN_VALIDATION_ETAPE
        // pour tester uniquement la verification d'habilitation.
        // unite non-null requis par la contrainte DB — le reseau est absent du JWT, pas de l'unite
        UUID demandeId = creerDemandeEnValidation(DA_SANS_RESEAU_ID, "UNITE-SANS-RESEAU");

        // Le DR du Reseau A tente de valider — mais le demandeur n'a pas de réseau
        // (l'API admin retourne 404 pour DA_SANS_RESEAU_ID/reseau).
        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDrReseauA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("RESEAU_NON_RENSEIGNE")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Cree une demande CONGE_ANNUEL directement en base au statut EN_VALIDATION_ETAPE,
     * avec un seul snapshot ROLE_FIXE_SCOPE_RESEAU a position 0 (seule etape intermediaire).
     * Apres validation, la machine a etats passera a EN_INSTRUCTION_ANALYSTE_RH.
     */
    private UUID creerDemandeEnValidation(String demandeurId, String reseauUnite) {
        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        demande.setDemandeurIdentifiantExterne(demandeurId);
        demande.setUniteIdentifiantExterne(reseauUnite);
        demande.setType(TypeAbsence.CONGE_ANNUEL);
        demande.setDateDebut(LocalDate.of(2026, 9, 1));
        demande.setDateFin(LocalDate.of(2026, 9, 10));
        demande.setNombreJours(8);
        demande.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
        demande.setPositionEtapeCourante(0);
        // circuitNom != "AGENT" -> DGConditionnelService.necessiteInjection() = false
        demande.setCircuitNom("Circuit Reseau - Conge Annuel");
        demande = (DemandeCongeAnnuel) demandeRepo.saveAndFlush(demande);

        // Unique etape intermediaire : ROLE_FIXE_SCOPE_RESEAU a position 0
        EtapeDemandeSnapshot snap = new EtapeDemandeSnapshot();
        snap.setDemandeId(demande.getId());
        snap.setOrdre(0);
        snap.setPosition(0);
        snap.setLibelle("DR Correspondant");
        snap.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU);
        snap.setRoleHabilite("DR");
        snap.setVerrouille(false);
        snapshotRepo.saveAndFlush(snap);

        return demande.getId();
    }

    private String buildJwt(String subject, String grade, String reseau,
                            List<String> roles) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("https://keycloak.banque.com/realms/afb")
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim(KeycloakClaims.REALM_ACCESS, Map.of(KeycloakClaims.ROLES, roles))
                .claim(KeycloakClaims.CLAIM_GRADE, grade);
        if (reseau != null) {
            builder.claim(KeycloakClaims.CLAIM_RESEAU, reseau);
        }
        JWTClaimsSet claims = builder.build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
