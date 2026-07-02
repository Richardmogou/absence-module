package com.banque.absences.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Résolution de la chaîne hiérarchique via l'API Admin Keycloak standard.
 *
 * <p>Toutes les méthodes utilisent l'API REST Admin Keycloak :
 * <ul>
 *   <li>Recherche par attribut : {@code GET /users?q=grade:X+reseau:Y}</li>
 *   <li>Lecture d'un utilisateur : {@code GET /users/{id}} → {@code UserRepresentation}</li>
 *   <li>Les attributs {@code grade}, {@code reseau}, {@code manager} sont lus
 *       depuis {@code UserRepresentation.attributes}</li>
 * </ul>
 */
@Service
public class HierarchicalChainResolver {

    private final RestClient keycloakAdminRestClient;

    public HierarchicalChainResolver(
            @Qualifier("keycloakAdminRestClient") RestClient keycloakAdminRestClient) {
        this.keycloakAdminRestClient = keycloakAdminRestClient;
    }

    // ── Lecture attributs ─────────────────────────────────────────────────────

    /**
     * Lit un attribut mono-valué depuis la {@code UserRepresentation} Keycloak.
     * Keycloak stocke tous les attributs custom sous la forme
     * {@code "attributes": {"grade": ["AGENT"], "reseau": ["AGENCE_01"]}}.
     */
    @SuppressWarnings("unchecked")
    private Optional<String> lireAttribut(Map<String, Object> user, String nomAttribut) {
        Object attrs = user.get("attributes");
        if (!(attrs instanceof Map<?, ?> attrMap)) return Optional.empty();
        Object valeurs = attrMap.get(nomAttribut);
        if (!(valeurs instanceof List<?> liste) || liste.isEmpty()) return Optional.empty();
        Object val = liste.get(0);
        return val != null && !val.toString().isBlank()
                ? Optional.of(val.toString())
                : Optional.empty();
    }

    /** Récupère la {@code UserRepresentation} complète pour un identifiant Keycloak. */
    private Optional<Map<String, Object>> fetchUser(String identifiantExterne) {
        try {
            Map<String, Object> user = keycloakAdminRestClient
                    .get()
                    .uri("/users/{id}", identifiantExterne)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() != 404) {
                            throw new KeycloakAdminException(
                                    "Erreur API Admin Keycloak : " + resp.getStatusCode());
                        }
                    })
                    .body(new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(user);
        } catch (KeycloakAdminException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakAdminException("Erreur lors de la lecture de l'utilisateur", e);
        }
    }

    // ── Résolution hiérarchique ───────────────────────────────────────────────

    /**
     * Résout le manager direct d'un employé.
     * Lit l'attribut {@code manager} de la {@code UserRepresentation} Keycloak,
     * qui contient l'identifiant externe du manager direct.
     */
    public Optional<String> resoudreManagerDirect(String employeIdentifiantExterne) {
        return fetchUser(employeIdentifiantExterne)
                .flatMap(u -> lireAttribut(u, "manager"));
    }

    /**
     * Résout le responsable hiérarchique à {@code profondeur} niveaux au-dessus.
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
     * au-dessus du demandeur.
     */
    public boolean verifierLienHierarchique(
            String validateurId, String demandeurId, int profondeur) {
        return resoudreHierarchique(demandeurId, profondeur)
                .map(r -> r.equals(validateurId))
                .orElse(false);
    }

    // ── Recherche par attributs ───────────────────────────────────────────────

    /**
     * Retourne les identifiants des collègues de même grade et même unité (réseau).
     * Utilisé pour proposer la liste des backups (N+0) au demandeur.
     *
     * <p>Utilise la recherche par attribut Keycloak : {@code GET /users?q=grade:X+reseau:Y}.
     * Le paramètre {@code q} accepte plusieurs prédicats {@code attribut:valeur} séparés
     * par un espace (encodé {@code +} ou {@code %20}).
     */
    public List<com.banque.absences.dto.EmployeDto> resoudreColleguesMemeGradeEtUnite(String grade, String unite) {
        if (grade == null || unite == null) return List.of();
        try {
            List<Map<String, Object>> resultats = keycloakAdminRestClient
                    .get()
                    .uri("/users?q=grade:{grade}+reseau:{unite}&max=50", grade, unite)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (resultats == null) return List.of();
            return resultats.stream()
                    .filter(u -> u.get("id") != null)
                    .map(u -> new com.banque.absences.dto.EmployeDto(
                            (String) u.get("id"),
                            (String) u.get("firstName"),
                            (String) u.get("lastName"),
                            (String) u.get("email")
                    ))
                    .toList();
        } catch (Exception e) {
            throw new KeycloakAdminException("Erreur lors de la recherche des collègues backup", e);
        }
    }

    /**
     * Retourne la liste des rôles disponibles dans Keycloak (realm roles).
     */
    public List<String> listerRolesKeycloak() {
        try {
            List<Map<String, Object>> resultats = keycloakAdminRestClient
                    .get()
                    .uri("/roles")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (resultats == null) return List.of();
            return resultats.stream()
                    .map(r -> (String) r.get("name"))
                    .filter(name -> name != null && !name.startsWith("default-") && !name.startsWith("offline_"))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new KeycloakAdminException("Erreur lors de la récupération des rôles Keycloak", e);
        }
    }

    /**
     * Retourne l'identifiant du premier employé trouvé pour un grade donné.
     * Utilisé comme employé-type représentatif pour le contrôle anti-doublon.
     */
    public Optional<String> resoudreEmployeTypeParGrade(String gradeDeclencheur) {
        try {
            List<Map<String, Object>> resultats = keycloakAdminRestClient
                    .get()
                    .uri("/users?q=grade:{grade}&max=1", gradeDeclencheur)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (resultats == null || resultats.isEmpty()) return Optional.empty();
            return Optional.ofNullable((String) resultats.get(0).get("id"));
        } catch (Exception e) {
            throw new KeycloakAdminException("Erreur lors de la recherche de l'employé-type", e);
        }
    }

    /**
     * Retourne le grade d'un employé via son attribut Keycloak {@code grade}.
     */
    public Optional<String> resoudreGradeParIdentifiant(String identifiantExterne) {
        return fetchUser(identifiantExterne)
                .flatMap(u -> lireAttribut(u, "grade"));
    }

    /**
     * Retourne le réseau (unité) de rattachement d'un employé via l'attribut {@code reseau}.
     */
    public Optional<String> resoudreReseau(String identifiantExterne) {
        return fetchUser(identifiantExterne)
                .flatMap(u -> lireAttribut(u, "reseau"));
    }

    /** Retourne le nom complet (prénom + nom) d'un utilisateur Keycloak. */
    public Optional<String> resolveNomComplet(String identifiantExterne) {
        return fetchUser(identifiantExterne).map(u -> {
            String prenom = (String) u.get("firstName");
            String nom    = (String) u.get("lastName");
            if (prenom != null && nom != null) return prenom + " " + nom;
            if (nom    != null) return nom;
            if (prenom != null) return prenom;
            return (String) u.get("username");
        });
    }

    // ── Exception interne ─────────────────────────────────────────────────────

    public static class KeycloakAdminException extends RuntimeException {
        public KeycloakAdminException(String message)                  { super(message); }
        public KeycloakAdminException(String message, Throwable cause) { super(message, cause); }
    }
}
