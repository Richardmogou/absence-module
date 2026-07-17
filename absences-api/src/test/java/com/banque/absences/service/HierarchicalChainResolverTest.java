package com.banque.absences.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * US-SEC-005 — Tests unitaires de {@link HierarchicalChainResolver}.
 *
 * Stratégie : construire le {@link RestClient} à partir d'un {@link RestTemplate}
 * intercepté par {@link MockRestServiceServer} — aucun appel HTTP réel,
 * aucun contexte Spring, aucun Keycloak.
 *
 * <p>Les mocks reproduisent le contrat de l'API Admin Keycloak standard :
 * {@code GET /users/{id}} renvoie une {@code UserRepresentation} JSON dont les attributs
 * custom vivent sous {@code attributes: {"manager": ["..."], "grade": ["..."]}} — et non
 * une valeur en texte brut. Le manager n'a pas de sous-ressource dédiée : il se lit dans
 * l'attribut {@code manager} de l'utilisateur.
 */
class HierarchicalChainResolverTest {

    private static final String BASE_URL   = "http://keycloak-mock/admin/realms/afb";
    private static final String EMPLOYE_ID = "employe-001";
    private static final String MANAGER1   = "manager-001";
    private static final String MANAGER2   = "manager-002";

    private MockRestServiceServer mockServer;
    private HierarchicalChainResolver resolver;

    @BeforeEach
    void setUp() {
        // RestTemplate instrumenté → RestClient délégant vers ce RestTemplate
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        RestClient restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(restTemplate.getRequestFactory())
                .build();

        resolver = new HierarchicalChainResolver(restClient);
    }

    /** UserRepresentation Keycloak portant un unique attribut custom. */
    private static String userAvecAttribut(String id, String attribut, String valeur) {
        return """
               {"id":"%s","username":"%s","attributes":{"%s":["%s"]}}
               """.formatted(id, id, attribut, valeur);
    }

    private void attendUser(String id, String corpsJson) {
        mockServer.expect(requestTo(BASE_URL + "/users/" + id))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess(corpsJson, MediaType.APPLICATION_JSON));
    }

    // ── resoudreManagerDirect ─────────────────────────────────────────────────

    @Test
    @DisplayName("resoudreManagerDirect lit l'attribut 'manager' de la UserRepresentation")
    void resoudreManagerDirect_retourneManagerSi200() {
        attendUser(EMPLOYE_ID, userAvecAttribut(EMPLOYE_ID, "manager", MANAGER1));

        Optional<String> result = resolver.resoudreManagerDirect(EMPLOYE_ID);

        assertThat(result).isPresent().hasValue(MANAGER1);
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreManagerDirect retourne Optional.empty() sur 404 sans lever d'exception")
    void resoudreManagerDirect_retourneVideSur404() {
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Ne doit pas lever d'exception
        Optional<String> result = resolver.resoudreManagerDirect(EMPLOYE_ID);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreManagerDirect retourne Optional.empty() si l'utilisateur n'a pas d'attribut manager")
    void resoudreManagerDirect_retourneVideSiAttributAbsent() {
        attendUser(EMPLOYE_ID, "{\"id\":\"" + EMPLOYE_ID + "\",\"attributes\":{\"grade\":[\"AGENT\"]}}");

        Optional<String> result = resolver.resoudreManagerDirect(EMPLOYE_ID);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreManagerDirect propage une KeycloakAdminException sur erreur 4xx autre que 404")
    void resoudreManagerDirect_propageErreurSi403() {
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> resolver.resoudreManagerDirect(EMPLOYE_ID))
                .isInstanceOf(HierarchicalChainResolver.KeycloakAdminException.class);
        mockServer.verify();
    }

    // ── resoudreHierarchique (2 niveaux) ──────────────────────────────────────

    @Test
    @DisplayName("resoudreHierarchique(employe, 2) retourne manager2 après 2 appels API")
    void resoudreHierarchique_deuxNiveaux_retourneManager2() {
        // Niveau 1 : employe → manager1
        attendUser(EMPLOYE_ID, userAvecAttribut(EMPLOYE_ID, "manager", MANAGER1));
        // Niveau 2 : manager1 → manager2
        attendUser(MANAGER1, userAvecAttribut(MANAGER1, "manager", MANAGER2));

        Optional<String> result = resolver.resoudreHierarchique(EMPLOYE_ID, 2);

        assertThat(result).isPresent().hasValue(MANAGER2);
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreHierarchique retourne Optional.empty() si la chaîne est rompue au niveau 1")
    void resoudreHierarchique_retourneVideSiChaineBrisee() {
        // Niveau 1 : employe → 404 (pas de manager)
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<String> result = resolver.resoudreHierarchique(EMPLOYE_ID, 2);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    // ── verifierLienHierarchique ──────────────────────────────────────────────

    @Test
    @DisplayName("verifierLienHierarchique retourne true quand le validateur est exactement à profondeur 2")
    void verifierLienHierarchique_retourneTrueSiCorrespondance() {
        attendUser(EMPLOYE_ID, userAvecAttribut(EMPLOYE_ID, "manager", MANAGER1));
        attendUser(MANAGER1, userAvecAttribut(MANAGER1, "manager", MANAGER2));

        boolean result = resolver.verifierLienHierarchique(MANAGER2, EMPLOYE_ID, 2);

        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("verifierLienHierarchique retourne false si le validateur ne correspond pas")
    void verifierLienHierarchique_retourneFalseSiAutreManager() {
        attendUser(EMPLOYE_ID, userAvecAttribut(EMPLOYE_ID, "manager", MANAGER1));
        attendUser(MANAGER1, userAvecAttribut(MANAGER1, "manager", MANAGER2));

        boolean result = resolver.verifierLienHierarchique("autre-manager", EMPLOYE_ID, 2);

        assertThat(result).isFalse();
        mockServer.verify();
    }

    // ── resoudreEmployeTypeParGrade (US-SEC-006) ───────────────────────────────

    @Test
    @DisplayName("resoudreEmployeTypeParGrade retourne Optional.empty() pour GRADE_FANTOME (liste vide)")
    void resoudreEmployeTypeParGrade_retourneVideSiListeVide() {
        mockServer.expect(requestTo(BASE_URL + "/users?q=grade:GRADE_FANTOME&max=1"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Optional<String> result = resolver.resoudreEmployeTypeParGrade("GRADE_FANTOME");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreEmployeTypeParGrade retourne l'id du premier utilisateur pour le grade DIRECTEUR")
    void resoudreEmployeTypeParGrade_retournePremierElementSiResultat() {
        // La recherche renvoie des UserRepresentation, pas des identifiants nus.
        mockServer.expect(requestTo(BASE_URL + "/users?q=grade:DIRECTEUR&max=1"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("[{\"id\":\"id-paul-ateba\",\"username\":\"paul.ateba\"}]",
                                          MediaType.APPLICATION_JSON));

        Optional<String> result = resolver.resoudreEmployeTypeParGrade("DIRECTEUR");

        assertThat(result).isPresent().hasValue("id-paul-ateba");
        mockServer.verify();
    }

    // ── resoudreGradeParIdentifiant (US-SEC-006) ───────────────────────────────

    @Test
    @DisplayName("resoudreGradeParIdentifiant lit l'attribut 'grade' de la UserRepresentation")
    void resoudreGradeParIdentifiant_retourneGradeSi200() {
        attendUser(EMPLOYE_ID, userAvecAttribut(EMPLOYE_ID, "grade", "DIRECTEUR"));

        Optional<String> result = resolver.resoudreGradeParIdentifiant(EMPLOYE_ID);

        assertThat(result).isPresent().hasValue("DIRECTEUR");
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreGradeParIdentifiant retourne Optional.empty() sur 404 sans lever d'exception")
    void resoudreGradeParIdentifiant_retourneVideSur404() {
        mockServer.expect(requestTo(BASE_URL + "/users/inconnu"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<String> result = resolver.resoudreGradeParIdentifiant("inconnu");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    // ── resoudreReseau ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("resoudreReseau lit l'attribut 'reseau' de la UserRepresentation")
    void resoudreReseau_retourneReseauSi200() {
        attendUser(EMPLOYE_ID, userAvecAttribut(EMPLOYE_ID, "reseau", "AGENCE_01"));

        Optional<String> result = resolver.resoudreReseau(EMPLOYE_ID);

        assertThat(result).isPresent().hasValue("AGENCE_01");
        mockServer.verify();
    }
}
