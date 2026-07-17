package com.banque.absences.service;

import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.dto.DoublonDetecteResult;
import com.banque.absences.exception.EmployeTypeIntrouvableException;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * US-ADM-003 — Scénario Directeur du modèle DA-ABSENCES-v5.0.
 *
 * Circuit testé :
 *   Étape 0 — HIERARCHIQUE (N+1, profondeur=1)  → doit résoudre vers grade "DG"
 *   Étape 1 — ROLE_FIXE_GLOBAL, roleKeycloakCible="DG"  → doublon détecté
 *
 * Chaîne Keycloak mockée via MockRestServiceServer :
 *   resoudreEmployeTypeParGrade("DIRECTEUR") → "id-paul-ateba"
 *   resoudreHierarchique("id-paul-ateba", 1) → "id-jean-mbarga"
 *   resoudreGradeParIdentifiant("id-jean-mbarga") → "DG"
 */
class CircuitCoherenceCheckerServiceTest {

    private static final String BASE_URL = "http://keycloak-mock/admin/realms/afb";

    private MockRestServiceServer         mockServer;
    private EtapeModeleCircuitRepository  etapeRepo;
    private CircuitCoherenceCheckerService service;

    // IDs des étapes du circuit de test
    private final UUID circuitId      = UUID.randomUUID();
    private final UUID etapeHId       = UUID.randomUUID();
    private final UUID etapeRoleFixeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        RestClient restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(restTemplate.getRequestFactory())
                .build();

        etapeRepo = mock(EtapeModeleCircuitRepository.class);
        HierarchicalChainResolver resolver = new HierarchicalChainResolver(restClient);
        service = new CircuitCoherenceCheckerService(etapeRepo, resolver);

        // Circuit : étape HIERARCHIQUE position=0, étape ROLE_FIXE_GLOBAL position=1
        EtapeModeleCircuit etapeH = etapeHierarchique(etapeHId, 0, 1);
        EtapeModeleCircuit etapeRF = etapeRoleFixeGlobal(etapeRoleFixeId, 1, "DG");

        when(etapeRepo.findByModeleCircuitIdOrderByOrdreAsc(circuitId))
                .thenReturn(List.of(etapeH, etapeRF));
    }

    /**
     * API Admin Keycloak standard : la recherche par attribut renvoie des UserRepresentation,
     * et les attributs custom vivent sous {@code attributes: {"grade": ["..."]}} — pas de
     * sous-ressource {@code /manager}, pas de valeur en texte brut.
     */
    private void attendRechercheParGrade(String grade, String idTrouve) {
        mockServer.expect(requestTo(BASE_URL + "/users?q=grade:" + grade + "&max=1"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("[{\"id\":\"" + idTrouve + "\"}]",
                                          MediaType.APPLICATION_JSON));
    }

    private void attendUser(String id, String attribut, String valeur) {
        mockServer.expect(requestTo(BASE_URL + "/users/" + id))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess(
                          "{\"id\":\"" + id + "\",\"attributes\":{\"" + attribut + "\":[\"" + valeur + "\"]}}",
                          MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("DA-ABSENCES-v5.0 — Directeur : doublon détecté entre étape HIERARCHIQUE (N+1→DG) et étape ROLE_FIXE_GLOBAL (DG)")
    void verifierCoherence_detecteDoublon_scenarioDirecteur() {
        // resoudreEmployeTypeParGrade("DIRECTEUR") → [{"id":"id-paul-ateba"}]
        attendRechercheParGrade("DIRECTEUR", "id-paul-ateba");

        // resoudreHierarchique("id-paul-ateba", 1) → attribut manager → "id-jean-mbarga"
        attendUser("id-paul-ateba", "manager", "id-jean-mbarga");

        // resoudreGradeParIdentifiant("id-jean-mbarga") → attribut grade → "DG"
        attendUser("id-jean-mbarga", "grade", "DG");

        Optional<DoublonDetecteResult> result =
                service.verifierCoherence(circuitId, "DIRECTEUR");

        assertThat(result).isPresent();
        assertThat(result.get().etapeHierarchiqueId()).isEqualTo(etapeHId);
        assertThat(result.get().etapeRoleFixeRedondanteId()).isEqualTo(etapeRoleFixeId);
        mockServer.verify();
    }

    @Test
    @DisplayName("Aucun doublon quand le grade résolu N+1 diffère du roleKeycloakCible de l'étape ROLE_FIXE")
    void verifierCoherence_aucunDoublon_gradesDifferents() {
        attendRechercheParGrade("DIRECTEUR", "id-paul-ateba");
        attendUser("id-paul-ateba", "manager", "id-jean-mbarga");

        // Le N+1 a grade "DIRECTEUR_GENERAL" ≠ "DG" → pas de doublon
        attendUser("id-jean-mbarga", "grade", "DIRECTEUR_GENERAL");

        Optional<DoublonDetecteResult> result =
                service.verifierCoherence(circuitId, "DIRECTEUR");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Optional.empty() si resoudreEmployeTypeParGrade lève EmployeTypeIntrouvableException pour un grade inconnu")
    void verifierCoherence_leveException_gradeInconnu() {
        mockServer.expect(requestTo(BASE_URL + "/users?q=grade:GRADE_INCONNU&max=1"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        org.junit.jupiter.api.Assertions.assertThrows(
                EmployeTypeIntrouvableException.class,
                () -> service.verifierCoherence(circuitId, "GRADE_INCONNU"));

        mockServer.verify();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static EtapeModeleCircuit etapeHierarchique(UUID id, int ordre, int profondeur) {
        EtapeModeleCircuit etape = new EtapeModeleCircuit();
        setId(etape, id);
        etape.setOrdre(ordre);
        etape.setLibelle("N+1 Hiérarchique");

        RegleAffectation regle = new RegleAffectation();
        regle.setEtapeModeleCircuit(etape);
        regle.setMecanisme(MecanismeResolution.HIERARCHIQUE);
        regle.setProfondeurHierarchique(profondeur);
        etape.getRegles().add(regle);
        return etape;
    }

    private static EtapeModeleCircuit etapeRoleFixeGlobal(UUID id, int ordre, String role) {
        EtapeModeleCircuit etape = new EtapeModeleCircuit();
        setId(etape, id);
        etape.setOrdre(ordre);
        etape.setLibelle("DG Role Fixe Global");

        RegleAffectation regle = new RegleAffectation();
        regle.setEtapeModeleCircuit(etape);
        regle.setMecanisme(MecanismeResolution.ROLE_FIXE_GLOBAL);
        regle.setRoleKeycloakCible(role);
        etape.getRegles().add(regle);
        return etape;
    }

    /** Injecte l'UUID via réflexion car le champ est non-settable (GeneratedValue). */
    private static void setId(EtapeModeleCircuit etape, UUID id) {
        try {
            java.lang.reflect.Field field = EtapeModeleCircuit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(etape, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
