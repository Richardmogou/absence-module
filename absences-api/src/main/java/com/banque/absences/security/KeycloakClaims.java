package com.banque.absences.security;

/**
 * Centralise TOUTES les dépendances Keycloak du projet (US-SEC-001 à US-SEC-006).
 *
 * RÈGLE ABSOLUE : ces constantes sont les SEULS endroits du code où les noms
 * de claims Keycloak apparaissent en dur. Tout autre composant les consomme
 * via cette classe, jamais en redéfinissant une chaîne littérale.
 *
 * Les valeurs marquées "À confirmer avec l'équipe IAM" doivent être mises à
 * jour dès validation du contrat d'intégration Keycloak.
 */
public final class KeycloakClaims {

    // ── Claims métier (à confirmer avec l'équipe IAM) ─────────────────────────

    /** Claim portant le grade de l'employé dans le token Keycloak. */
    public static final String CLAIM_GRADE   = "grade";

    /** Claim portant le réseau/agence de rattachement de l'employé. */
    public static final String CLAIM_RESEAU  = "reseau";

    /** Claim portant l'identifiant du manager hiérarchique direct. */
    public static final String CLAIM_MANAGER = "manager";

    // ── Claims Keycloak standard (structure realm_access) ─────────────────────

    /** Claim de premier niveau contenant les rôles du realm. */
    public static final String REALM_ACCESS  = "realm_access";

    /** Clé de la liste des rôles dans le claim realm_access. */
    public static final String ROLES         = "roles";

    /** Préfixe Spring Security appliqué à chaque rôle extrait. */
    public static final String ROLE_PREFIX   = "ROLE_";

    private KeycloakClaims() {}
}
