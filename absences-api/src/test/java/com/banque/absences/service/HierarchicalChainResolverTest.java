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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * US-SEC-005 — Tests unitaires de {@link HierarchicalChainResolver}.
 *
 * Stratégie : construire le {@link RestClient} à partir d'un {@link RestTemplate}
 * intercepté par {@link MockRestServiceServer} — aucun appel HTTP réel,
 * aucun contexte Spring, aucun Keycloak.
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

    // ── resoudreManagerDirect ─────────────────────────────────────────────────

    @Test
    @DisplayName("resoudreManagerDirect retourne le manager quand l'API répond 200")
    void resoudreManagerDirect_retourneManagerSi200() {
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID + "/manager"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess(MANAGER1, MediaType.TEXT_PLAIN));

        Optional<String> result = resolver.resoudreManagerDirect(EMPLOYE_ID);

        assertThat(result).isPresent().hasValue(MANAGER1);
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreManagerDirect retourne Optional.empty() sur 404 sans lever d'exception")
    void resoudreManagerDirect_retourneVideSur404() {
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID + "/manager"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Ne doit pas lever d'exception
        Optional<String> result = resolver.resoudreManagerDirect(EMPLOYE_ID);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    // ── resoudreHierarchique (2 niveaux) ──────────────────────────────────────

    @Test
    @DisplayName("resoudreHierarchique(employe, 2) retourne manager2 après 2 appels API")
    void resoudreHierarchique_deuxNiveaux_retourneManager2() {
        // Niveau 1 : employe → manager1
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID + "/manager"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess(MANAGER1, MediaType.TEXT_PLAIN));

        // Niveau 2 : manager1 → manager2
        mockServer.expect(requestTo(BASE_URL + "/users/" + MANAGER1 + "/manager"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess(MANAGER2, MediaType.TEXT_PLAIN));

        Optional<String> result = resolver.resoudreHierarchique(EMPLOYE_ID, 2);

        assertThat(result).isPresent().hasValue(MANAGER2);
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreHierarchique retourne Optional.empty() si la chaîne est rompue au niveau 1")
    void resoudreHierarchique_retourneVideSiChaineBrisee() {
        // Niveau 1 : employe → 404 (pas de manager)
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID + "/manager"))
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
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID + "/manager"))
                  .andRespond(withSuccess(MANAGER1, MediaType.TEXT_PLAIN));
        mockServer.expect(requestTo(BASE_URL + "/users/" + MANAGER1 + "/manager"))
                  .andRespond(withSuccess(MANAGER2, MediaType.TEXT_PLAIN));

        boolean result = resolver.verifierLienHierarchique(MANAGER2, EMPLOYE_ID, 2);

        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("verifierLienHierarchique retourne false si le validateur ne correspond pas")
    void verifierLienHierarchique_retourneFalseSiAutreManager() {
        mockServer.expect(requestTo(BASE_URL + "/users/" + EMPLOYE_ID + "/manager"))
                  .andRespond(withSuccess(MANAGER1, MediaType.TEXT_PLAIN));
        mockServer.expect(requestTo(BASE_URL + "/users/" + MANAGER1 + "/manager"))
                  .andRespond(withSuccess(MANAGER2, MediaType.TEXT_PLAIN));

        boolean result = resolver.verifierLienHierarchique("autre-manager", EMPLOYE_ID, 2);

        assertThat(result).isFalse();
        mockServer.verify();
    }

    // ── resoudreEmployeTypeParGrade (US-SEC-006) ───────────────────────────────

    @Test
    @DisplayName("resoudreEmployeTypeParGrade retourne Optional.empty() pour GRADE_FANTOME (liste vide)")
    void resoudreEmployeTypeParGrade_retourneVideSiListeVide() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/users?grade={grade}&limit=1", "GRADE_FANTOME"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        Optional<String> result = resolver.resoudreEmployeTypeParGrade("GRADE_FANTOME");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("resoudreEmployeTypeParGrade retourne Optional.of('id-paul-ateba') pour le grade DIRECTEUR")
    void resoudreEmployeTypeParGrade_retournePremierElementSiResultat() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/users?grade={grade}&limit=1", "DIRECTEUR"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("[\"id-paul-ateba\"]", MediaType.APPLICATION_JSON));

        Optional<String> result = resolver.resoudreEmployeTypeParGrade("DIRECTEUR");

        assertThat(result).isPresent().hasValue("id-paul-ateba");
        mockServer.verify();
    }

    // ── resoudreGradeParIdentifiant (US-SEC-006) ───────────────────────────────

    @Test
    @DisplayName("resoudreGradeParIdentifiant retourne le grade quand l'API répond 200")
    void resoudreGradeParIdentifiant_retourneGradeSi200() {
        mockServer.expect(requestTo(BASE_URL + "/users/employe-001"))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("DIRECTEUR", MediaType.TEXT_PLAIN));

        Optional<String> result = resolver.resoudreGradeParIdentifiant("employe-001");

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
}
