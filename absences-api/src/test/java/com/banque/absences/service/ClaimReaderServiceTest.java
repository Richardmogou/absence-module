package com.banque.absences.service;

import com.banque.absences.security.KeycloakClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-SEC-003 / US-SEC-004 — Tests unitaires purs de {@link ClaimReaderService}.
 *
 * Stratégie : construire un {@link Jwt} en mémoire via son builder,
 * l'injecter dans le {@link SecurityContextHolder}, puis vérifier les résultats.
 * Aucun contexte Spring, aucun appel HTTP, aucune base de données.
 */
class ClaimReaderServiceTest {

    private final ClaimReaderService service = new ClaimReaderService();

    // ── Nettoyage du SecurityContext après chaque test ────────────────────────

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── lireClaimGrade ────────────────────────────────────────────────────────

    @Test
    @DisplayName("US-SEC-003 — lireClaimGrade retourne exactement 'DA' quand le claim est présent")
    void lireClaimGrade_retourneValeur() {
        injecterJwt(Map.of(KeycloakClaims.CLAIM_GRADE, "DA"), "sub-001");

        assertThat(service.lireClaimGrade()).isEqualTo("DA");
    }

    @Test
    @DisplayName("US-SEC-003 — lireClaimGrade retourne null quand le claim est absent")
    void lireClaimGrade_retourneNullSiAbsent() {
        injecterJwt(Map.of(), "sub-001");

        assertThat(service.lireClaimGrade()).isNull();
    }

    // ── lireClaimReseau ───────────────────────────────────────────────────────

    @Test
    @DisplayName("US-SEC-004 — lireClaimReseau retourne la valeur quand le claim est présent")
    void lireClaimReseau_retourneOptionalAvecValeur() {
        injecterJwt(Map.of(KeycloakClaims.CLAIM_RESEAU, "RESEAU-OUEST"), "sub-001");

        assertThat(service.lireClaimReseau())
                .isPresent()
                .hasValue("RESEAU-OUEST");
    }

    @Test
    @DisplayName("US-SEC-004 — lireClaimReseau retourne Optional.empty() sans exception quand le claim est absent")
    void lireClaimReseau_retourneOptionalVideSiAbsent() {
        // JWT sans CLAIM_RESEAU — ne doit pas lever d'exception
        injecterJwt(Map.of(KeycloakClaims.CLAIM_GRADE, "DA"), "sub-001");

        Optional<String> resultat = service.lireClaimReseau();

        assertThat(resultat).isEmpty();
    }

    // ── lireClaimManager ─────────────────────────────────────────────────────

    @Test
    @DisplayName("US-SEC-004 — lireClaimManager retourne la valeur quand le claim est présent")
    void lireClaimManager_retourneOptionalAvecValeur() {
        injecterJwt(Map.of(KeycloakClaims.CLAIM_MANAGER, "manager-99"), "sub-001");

        assertThat(service.lireClaimManager())
                .isPresent()
                .hasValue("manager-99");
    }

    @Test
    @DisplayName("US-SEC-004 — lireClaimManager retourne Optional.empty() sans exception quand le claim est absent")
    void lireClaimManager_retourneOptionalVideSiAbsent() {
        injecterJwt(Map.of(), "sub-001");

        assertThat(service.lireClaimManager()).isEmpty();
    }

    // ── identifiantUtilisateurCourant ─────────────────────────────────────────

    @Test
    @DisplayName("US-SEC-003 — identifiantUtilisateurCourant retourne le subject du JWT")
    void identifiantUtilisateurCourant_retourneSubject() {
        injecterJwt(Map.of(), "employe-42");

        assertThat(service.identifiantUtilisateurCourant()).isEqualTo("employe-42");
    }

    // ── Cas d'erreur ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("IllegalStateException si appelé hors contexte authentifié")
    void lireClaimGrade_leveExceptionSiContexteVide() {
        // SecurityContext vide (pas d'Authentication injectée)
        assertThatThrownBy(() -> service.lireClaimGrade())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JwtAuthenticationToken");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Construit un {@link Jwt} minimal via son builder officiel Spring Security
     * et l'injecte dans le {@link SecurityContextHolder}.
     * Aucun signing, aucun appel réseau.
     */
    private static void injecterJwt(Map<String, Object> claimsSupplementaires, String subject) {
        Instant maintenant = Instant.now();

        var jwtBuilder = Jwt.withTokenValue("token-de-test")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject(subject)
                .issuer("https://keycloak.banque.com/realms/afb")
                .issuedAt(maintenant.minusSeconds(60))
                .expiresAt(maintenant.plusSeconds(300))
                .claim(KeycloakClaims.REALM_ACCESS,
                        Map.of(KeycloakClaims.ROLES, List.of("EMPLOYE")));

        claimsSupplementaires.forEach(jwtBuilder::claim);

        Jwt jwt = jwtBuilder.build();

        var authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
