package com.banque.absences.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Convertisseur JWT Keycloak (US-SEC-001).
 *
 * Lit les rôles depuis {@code realm_access.roles} du token Keycloak
 * et les expose sous la forme {@code ROLE_ADMIN_RH}, {@code ROLE_ANALYSTE_RH},
 * {@code ROLE_DRH}, etc. (préfixe ROLE_ + rôle en majuscules).
 *
 * Les noms de claims sont exclusivement issus de {@link KeycloakClaims} —
 * aucune chaîne littérale Keycloak ne doit apparaître ici.
 */
@Component
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;

    public KeycloakJwtConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("");   // désactive le préfixe SCOPE_ par défaut

        this.delegate = new JwtAuthenticationConverter();
        this.delegate.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        this.delegate.setPrincipalClaimName("sub");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(KeycloakClaims.REALM_ACCESS);
        if (realmAccess == null) return List.of();

        List<String> roles = (List<String>) realmAccess.getOrDefault(KeycloakClaims.ROLES, List.of());
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(
                        KeycloakClaims.ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
