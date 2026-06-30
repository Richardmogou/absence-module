package com.banque.absences.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * US-SEC-005 — Résolution de la chaîne hiérarchique d'un employé
 * via l'API d'administration Keycloak.
 *
 * Utilise le {@link RestClient} du compte de service configuré dans
 * {@link com.banque.absences.config.KeycloakAdminClientConfig}.
 * Aucune chaîne littérale Keycloak n'apparaît ici.
 */
@Service
public class HierarchicalChainResolver {

    private final RestClient keycloakAdminRestClient;

    public HierarchicalChainResolver(
            @Qualifier("keycloakAdminRestClient") RestClient keycloakAdminRestClient) {
        this.keycloakAdminRestClient = keycloakAdminRestClient;
    }

    /**
     * Résout le manager direct d'un employé (profondeur 1).
     *
     * @param employeIdentifiantExterne identifiant Keycloak de l'employé
     * @return identifiant du manager, ou {@link Optional#empty()} si l'employé
     *         n'a pas de manager ou est introuvable (404)
     */
    public Optional<String> resoudreManagerDirect(String employeIdentifiantExterne) {
        try {
            String manager = keycloakAdminRestClient
                    .get()
                    .uri("/users/{id}/manager", employeIdentifiantExterne)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        // 404 = pas de manager, toute autre 4xx remonte
                        if (resp.getStatusCode().value() != 404) {
                            throw new KeycloakAdminException(
                                    "Erreur API Admin Keycloak : " + resp.getStatusCode());
                        }
                        // 404 → on ne lève rien, body() retournera null
                    })
                    .body(String.class);
            return Optional.ofNullable(manager);
        } catch (KeycloakAdminException e) {
            throw e;
        } catch (Exception e) {
            // Toute autre erreur réseau inattendue remonte explicitement
            throw new KeycloakAdminException("Erreur lors de la résolution du manager", e);
        }
    }

    /**
     * Résout le responsable hiérarchique à {@code profondeur} niveaux au-dessus
     * de l'employé donné.
     *
     * @param employeIdentifiantExterne identifiant Keycloak de départ
     * @param profondeur                nombre de niveaux à remonter (≥ 1)
     * @return identifiant du responsable, ou vide si la chaîne est rompue
     */
    public Optional<String> resoudreHierarchique(
            String employeIdentifiantExterne, int profondeur) {
        String courant = employeIdentifiantExterne;
        for (int i = 0; i < profondeur; i++) {
            Optional<String> suivant = resoudreManagerDirect(courant);
            if (suivant.isEmpty()) return Optional.empty();
            courant = suivant.get();
        }
        return Optional.of(courant);
    }

    /**
     * Vérifie qu'un validateur se trouve exactement à {@code profondeur} niveaux
     * au-dessus du demandeur dans la hiérarchie Keycloak.
     *
     * @param validateurId identifiant Keycloak du validateur
     * @param demandeurId  identifiant Keycloak du demandeur
     * @param profondeur   niveaux hiérarchiques à remonter
     * @return {@code true} si le responsable trouvé correspond au validateur
     */
    public boolean verifierLienHierarchique(
            String validateurId, String demandeurId, int profondeur) {
        return resoudreHierarchique(demandeurId, profondeur)
                .map(r -> r.equals(validateurId))
                .orElse(false);
    }

    // ── US-SEC-006 ────────────────────────────────────────────────────────────

    /**
     * Retourne l'identifiant Keycloak du premier employé trouvé pour un grade donné.
     * Utilisé comme employé-type représentatif pour le contrôle anti-doublon (Sprint 6).
     *
     * <p>Ne lève jamais d'exception si aucun résultat : retourne {@link Optional#empty()}.
     * La gestion du code {@code EMPLOYE_TYPE_INTROUVABLE} est traitée au Sprint 6 (P46).
     *
     * @param gradeDeclencheur valeur du claim grade à rechercher
     * @return identifiant du premier employé correspondant, ou vide
     */
    /**
     * Retourne les identifiants des collègues partageant le même grade et la même unité.
     * Utilisé pour proposer la liste des backups possibles au demandeur.
     */
    public List<String> resoudreColleguesMemeGradeEtUnite(String grade, String unite) {
        if (grade == null || unite == null) return List.of();
        List<String> resultats = keycloakAdminRestClient
                .get()
                .uri("/users?grade={grade}&unite={unite}", grade, unite)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return resultats != null ? resultats : List.of();
    }

    public Optional<String> resoudreEmployeTypeParGrade(String gradeDeclencheur) {
        List<String> resultats = keycloakAdminRestClient
                .get()
                .uri("/users?grade={grade}&limit=1", gradeDeclencheur)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (resultats == null || resultats.isEmpty()) return Optional.empty();
        return Optional.of(resultats.get(0));
    }

    /**
     * Retourne le grade Keycloak d'un employé identifié par son identifiant externe.
     *
     * @param identifiantExterne identifiant Keycloak de l'employé
     * @return grade de l'employé, ou {@link Optional#empty()} si introuvable (404)
     */
    public Optional<String> resoudreGradeParIdentifiant(String identifiantExterne) {
        String grade = keycloakAdminRestClient
                .get()
                .uri("/users/{id}", identifiantExterne)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    if (resp.getStatusCode().value() != 404) {
                        throw new KeycloakAdminException(
                                "Erreur API Admin Keycloak : " + resp.getStatusCode());
                    }
                })
                .body(String.class);
        return Optional.ofNullable(grade);
    }

    /**
     * Retourne le réseau de rattachement d'un employé via l'API Admin Keycloak.
     *
     * @param identifiantExterne identifiant Keycloak de l'employé
     * @return réseau, ou {@link Optional#empty()} si le claim est absent (404)
     */
    public Optional<String> resoudreReseau(String identifiantExterne) {
        try {
            String reseau = keycloakAdminRestClient
                    .get()
                    .uri("/users/{id}/reseau", identifiantExterne)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() != 404) {
                            throw new KeycloakAdminException(
                                    "Erreur API Admin Keycloak : " + resp.getStatusCode());
                        }
                    })
                    .body(String.class);
            return Optional.ofNullable(reseau);
        } catch (KeycloakAdminException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakAdminException("Erreur lors de la résolution du réseau", e);
        }
    }

    // ── Exception interne ─────────────────────────────────────────────────────

    public static class KeycloakAdminException extends RuntimeException {
        public KeycloakAdminException(String message) { super(message); }
        public KeycloakAdminException(String message, Throwable cause) { super(message, cause); }
    }
}
