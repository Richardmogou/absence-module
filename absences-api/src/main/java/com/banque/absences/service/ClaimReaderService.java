package com.banque.absences.service;

import com.banque.absences.security.KeycloakClaims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * US-SEC-003 / US-SEC-004 — Lecture des claims métier du JWT de la requête courante.
 *
 * Toutes les méthodes lisent exclusivement le token déjà validé présent dans le
 * SecurityContext. Aucun appel HTTP sortant n'est effectué.
 *
 * Les noms de claims sont issus exclusivement de {@link KeycloakClaims} —
 * aucune chaîne littérale Keycloak n'apparaît dans ce service.
 */
@Service
public class ClaimReaderService {

    /**
     * Retourne le grade de l'employé (claim {@link KeycloakClaims#CLAIM_GRADE}).
     *
     * @return la valeur du claim, ou {@code null} si absent du token
     */
    public String lireClaimGrade() {
        return jwtCourant().getClaimAsString(KeycloakClaims.CLAIM_GRADE);
    }

    /**
     * Retourne le réseau/agence de rattachement (claim {@link KeycloakClaims#CLAIM_RESEAU}).
     *
     * @return {@code Optional} contenant la valeur, ou vide si le claim est absent
     */
    public Optional<String> lireClaimReseau() {
        return Optional.ofNullable(
                jwtCourant().getClaimAsString(KeycloakClaims.CLAIM_RESEAU));
    }

    /**
     * Retourne l'identifiant du manager hiérarchique (claim {@link KeycloakClaims#CLAIM_MANAGER}).
     *
     * @return {@code Optional} contenant la valeur, ou vide si le claim est absent
     */
    public Optional<String> lireClaimManager() {
        return Optional.ofNullable(
                jwtCourant().getClaimAsString(KeycloakClaims.CLAIM_MANAGER));
    }

    /**
     * Retourne le subject (identifiant unique) de l'utilisateur courant.
     *
     * @return la valeur du claim {@code sub}
     */
    public String identifiantUtilisateurCourant() {
        return jwtCourant().getSubject();
    }

    public boolean estRolePrivilegie() {
        return getRoles().stream()
                .anyMatch(Set.of("ROLE_ANALYSTE_RH", "ROLE_DRH", "ROLE_ADMIN_RH")::contains);
    }

    public java.util.List<String> getRoles() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    // ── Accès au JWT ──────────────────────────────────────────────────────────

    private Jwt jwtCourant() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException(
                    "Aucun JwtAuthenticationToken dans le SecurityContext — " +
                    "méthode appelée hors d'une requête authentifiée ?");
        }
        return jwtAuth.getToken();
    }
}
