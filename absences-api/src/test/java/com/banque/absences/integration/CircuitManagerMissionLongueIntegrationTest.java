package com.banque.absences.integration;

import com.banque.absences.testsupport.KeycloakAdminMock;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.domain.TypeAbsence;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitManagerMissionLongueIntegrationTest {

    private static final String MANAGER_ID = "manager-test-042";
    private static final String BACKUP_ID = "back-up-test-043";
    private static final String CHEF_ID = "chef-processus-test-050";
    private static final String DG_ID = "dg-test-099";
    private static final String UNITE = "UNITE-MANAGER-MLG-001";

    private static MockWebServer jwksMockServer;
    private static MockWebServer adminApiMockServer;
    private static RSAKey rsaKey;

    private String tokenManager;
    private String tokenBackup;
    private String tokenChef;
    private String tokenDg;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DemandeAbsenceRepository demandeRepo;
    @Autowired private ModeleCircuitRepository circuitRepo;
    @Autowired private EtapeModeleCircuitRepository etapeModeleRepo;
    @Autowired private RegleAffectationRepository regleRepo;
    @Autowired private EtapeDemandeSnapshotRepository snapshotRepo;

    @MockBean private DoublonDetectionService doublonService;
    @MockBean private CircuitDeterminationService circuitService;

    @DynamicPropertySource
    static void injecterUrls(DynamicPropertyRegistry registry) throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-manager-mlg").generate();
        jwksMockServer = new MockWebServer();
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

        // Chaîne hiérarchique : manager → chef de processus → DG.
        adminApiMockServer.setDispatcher(KeycloakAdminMock.dispatcher(Map.of(
                MANAGER_ID, KeycloakAdminMock.utilisateur().grade("MANAGER").manager(CHEF_ID),
                CHEF_ID,    KeycloakAdminMock.utilisateur().grade("DA").manager(DG_ID),
                DG_ID,      KeycloakAdminMock.utilisateur().grade("DG"))));

        registry.add("keycloak.jwks-uri",
                () -> jwksMockServer.url("/realms/afb/protocol/openid-connect/certs").toString());
        registry.add("keycloak.admin-api-base-url",
                () -> adminApiMockServer.url("").toString());
    }

    @BeforeAll
    void initTokensEtMocks() throws Exception {
        tokenManager = buildJwt(MANAGER_ID, "MANAGER", List.of("EMPLOYE", "MANAGER"));
        tokenBackup = buildJwt(BACKUP_ID, "AGENT", List.of("EMPLOYE"));
        tokenChef = buildJwt(CHEF_ID, "CHEF_PROCESSUS", List.of("EMPLOYE", "CHEF_PROCESSUS"));
        tokenDg = buildJwt(DG_ID, "DG", List.of("EMPLOYE", "DG"));
        when(doublonService.detecterDoublon(any())).thenReturn(false);
    }

    @AfterAll
    void tearDown() throws Exception {
        jwksMockServer.close();
        adminApiMockServer.close();
    }

    @Test
    @DisplayName("US-MLG-003 — Circuit Manager MISSION_LONGUE : une seule decision DG hierarchique")
    void circuitManagerMissionLongue_uneSeuleDecisionDgHierarchique() throws Exception {
        UUID demandeId = creerDemandeMissionLongue();
        ModeleCircuit circuit = creerCircuitManagerMissionLongueEnBase();
        when(circuitService.determinerCircuitApplicable(any()))
                .thenReturn(Optional.of(circuit));

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/soumettre")
                        .header("Authorization", "Bearer " + tokenManager))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statut", is("EN_VALIDATION_ETAPE")))
                .andExpect(jsonPath("$.positionEtapeCourante", is(0)))
                .andExpect(jsonPath("$.circuitId", notNullValue()));

        enrichirSnapshotsPourCircuitManager(demandeId);

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenBackup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(1)));

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenChef)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionEtapeCourante", is(2)));

        assertThat(nombreDecisionsDg(demandeId)).isZero();

        mockMvc.perform(post("/api/v5/demandes/" + demandeId + "/validation")
                        .header("Authorization", "Bearer " + tokenDg)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VALIDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut", is("EN_INSTRUCTION_ANALYSTE_RH")));

        assertThat(nombreDecisionsDg(demandeId)).isEqualTo(1);
        assertThat(mecanismeDecisionDg(demandeId))
                .isEqualTo(MecanismeResolution.HIERARCHIQUE.name());
    }

    private UUID creerDemandeMissionLongue() throws Exception {
        String body = """
                {"type":"MISSION_LONGUE","dateDebut":"2026-09-01","nombreJours":20}
                """;

        String response = mockMvc.perform(post("/api/v5/demandes")
                        .header("Authorization", "Bearer " + tokenManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut", is("BROUILLON")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(mapper.readTree(response).get("id").asText());
    }

    private void enrichirSnapshotsPourCircuitManager(UUID demandeId) {
        List<EtapeDemandeSnapshot> snaps =
                snapshotRepo.findByDemandeIdOrderByOrdreAsc(demandeId);
        for (EtapeDemandeSnapshot snap : snaps) {
            snap.setPosition(snap.getOrdre());
            if (snap.getOrdre() == 0) {
                snap.setMecanismeResolution(null);
                snap.setValidateurIdentifiantExterne(UNITE);
            } else if (snap.getOrdre() == 1) {
                snap.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
                snap.setRoleHabilite("CHEF_PROCESSUS");
            } else if (snap.getOrdre() == 2) {
                snap.setMecanismeResolution(MecanismeResolution.HIERARCHIQUE);
                snap.setRoleHabilite("DG");
            } else {
                snap.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_GLOBAL);
                snap.setRoleHabilite("ANALYSTE_RH");
            }
            snapshotRepo.saveAndFlush(snap);
        }

        DemandeAbsence demande = demandeRepo.findById(demandeId).orElseThrow();
        demande.setUniteIdentifiantExterne(UNITE);
        demandeRepo.saveAndFlush(demande);
    }

    private int nombreDecisionsDg(UUID demandeId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM validation v
                JOIN etape_demande_snapshot eds ON v.etape_snapshot_id = eds.id
                WHERE v.demande_id = ? AND eds.role_habilite = 'DG'
                """, Integer.class, demandeId);
        return count == null ? 0 : count;
    }

    private String mecanismeDecisionDg(UUID demandeId) {
        return jdbcTemplate.queryForObject("""
                SELECT eds.mecanisme_resolution FROM validation v
                JOIN etape_demande_snapshot eds ON v.etape_snapshot_id = eds.id
                WHERE v.demande_id = ? AND eds.role_habilite = 'DG'
                """, String.class, demandeId);
    }

    private ModeleCircuit creerCircuitManagerMissionLongueEnBase() {
        ModeleCircuit circuit = new ModeleCircuit();
        circuit.setNom("Circuit Manager - Mission Longue");
        circuit.setTypeAbsenceCible(TypeAbsence.MISSION_LONGUE);
        circuit.setActif(true);
        circuit = circuitRepo.saveAndFlush(circuit);

        ajouterEtape(circuit, 0, "Back-up hierarchique", null, null, null);
        ajouterEtape(circuit, 1, "Chef de Processus", MecanismeResolution.HIERARCHIQUE, 1, "CHEF_PROCESSUS");
        ajouterEtape(circuit, 2, "DG", MecanismeResolution.HIERARCHIQUE, 2, "DG");
        ajouterEtape(circuit, 3, "Instruction Analyste RH", MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU, null, "ANALYSTE_RH");

        return circuit;
    }

    private void ajouterEtape(ModeleCircuit circuit, int ordre, String libelle,
                              MecanismeResolution mecanisme, Integer profondeur,
                              String roleHabilite) {
        EtapeModeleCircuit etape = new EtapeModeleCircuit();
        etape.setModeleCircuit(circuit);
        etape.setOrdre(ordre);
        etape.setLibelle(libelle);
        etape.setDelaiJours(5);
        etape.setEstVerrouillable(false);
        etape = etapeModeleRepo.saveAndFlush(etape);

        if (mecanisme != null) {
            RegleAffectation regle = new RegleAffectation();
            regle.setEtapeModeleCircuit(etape);
            regle.setMecanisme(mecanisme);
            regle.setProfondeurHierarchique(profondeur);
            regle.setRoleKeycloakCible(roleHabilite);
            regle.setPriorite(1);
            regleRepo.saveAndFlush(regle);
        }
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
