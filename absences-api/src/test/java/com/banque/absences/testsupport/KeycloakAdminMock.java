package com.banque.absences.testsupport;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simulation de l'API Admin Keycloak pour les tests d'intégration.
 *
 * <p>Reproduit le contrat réellement consommé par {@code HierarchicalChainResolver} :
 * <ul>
 *   <li>{@code GET /users/{id}} → {@code UserRepresentation} JSON ; il n'existe pas de
 *       sous-ressource {@code /users/{id}/manager} — le manager est un attribut custom.</li>
 *   <li>Les attributs custom ({@code manager}, {@code grade}, {@code reseau}) sont
 *       multi-valués : {@code "attributes": {"manager": ["id-du-manager"]}}.</li>
 *   <li>{@code GET /users?q=grade:X&max=N} → liste de {@code UserRepresentation}.</li>
 * </ul>
 *
 * <p>Centralisé ici parce que six tests d'intégration décrivaient chacun leur propre
 * dispatcher : la migration vers l'API Keycloak standard les avait tous laissés sur
 * l'ancien contrat en texte brut, sans que rien ne le signale.
 */
public final class KeycloakAdminMock {

    private KeycloakAdminMock() {}

    /** Décrit un utilisateur et ses attributs custom. */
    public static final class Utilisateur {
        private final Map<String, String> attributs = new LinkedHashMap<>();

        public Utilisateur manager(String managerId) { attributs.put("manager", managerId); return this; }
        public Utilisateur grade(String grade)       { attributs.put("grade",   grade);     return this; }
        public Utilisateur reseau(String reseau)     { attributs.put("reseau",  reseau);    return this; }
        public Utilisateur attribut(String nom, String valeur) { attributs.put(nom, valeur); return this; }

        Map<String, String> attributs() { return attributs; }
    }

    public static Utilisateur utilisateur() {
        return new Utilisateur();
    }

    /** Sérialise une {@code UserRepresentation} Keycloak. */
    public static String userJson(String id, Utilisateur u) {
        String attrs = u.attributs().entrySet().stream()
                .map(e -> "\"%s\":[\"%s\"]".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(","));
        return "{\"id\":\"%s\",\"username\":\"%s\",\"attributes\":{%s}}".formatted(id, id, attrs);
    }

    /**
     * Dispatcher servant les utilisateurs fournis sur {@code GET /users/{id}}, et la
     * recherche {@code GET /users?q=grade:X} sur leurs attributs. Tout le reste : 404.
     *
     * @param utilisateurs identifiant Keycloak → attributs
     */
    public static Dispatcher dispatcher(Map<String, Utilisateur> utilisateurs) {
        return new Dispatcher() {
            @Override @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest req) {
                String path = req.getPath() == null ? "" : req.getPath();

                // Recherche par attribut : /users?q=grade:DIRECTEUR&max=1
                if (path.contains("/users?q=") || path.contains("/users?")) {
                    String grade = extraireGradeRecherche(path);
                    String corps = utilisateurs.entrySet().stream()
                            .filter(e -> grade != null
                                    && grade.equals(e.getValue().attributs().get("grade")))
                            .map(e -> userJson(e.getKey(), e.getValue()))
                            .collect(Collectors.joining(",", "[", "]"));
                    return json(corps);
                }

                // Lecture d'un utilisateur : /users/{id}
                int i = path.indexOf("/users/");
                if (i >= 0) {
                    String id = path.substring(i + "/users/".length());
                    int q = id.indexOf('?');
                    if (q >= 0) id = id.substring(0, q);
                    Utilisateur u = utilisateurs.get(id);
                    if (u != null) return json(userJson(id, u));
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static String extraireGradeRecherche(String path) {
        int i = path.indexOf("grade:");
        if (i < 0) return null;
        String reste = path.substring(i + "grade:".length());
        // Le q peut porter plusieurs prédicats (grade:X reseau:Y), encodés + ou %20
        for (String sep : new String[]{"&", "+", "%20", " "}) {
            int j = reste.indexOf(sep);
            if (j >= 0) reste = reste.substring(0, j);
        }
        return reste;
    }

    private static MockResponse json(String corps) {
        return new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(corps);
    }
}
