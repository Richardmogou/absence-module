package com.banque.absences.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/**
 * US-SEC-005 — Configure l'accès à l'API d'administration Keycloak via
 * un compte de service (client_credentials grant).
 *
 * Le {@link RestClient} produit injecte automatiquement un Bearer token
 * obtenu via client_credentials avant chaque requête sortante.
 */
@Configuration
public class KeycloakAdminClientConfig {

    /** Identifiant de registration utilisé en interne par Spring Security OAuth2. */
    private static final String REGISTRATION_ID = "keycloak-admin";

    /**
     * Enregistrement OAuth2 du compte de service, construit depuis
     * {@link KeycloakProperties} (aucune chaîne littérale Keycloak ici).
     */
    @Bean
    public ClientRegistrationRepository keycloakAdminClientRegistrationRepository(
            KeycloakProperties props) {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId(REGISTRATION_ID)
                .clientId(props.getAdminClientId())
                .clientSecret(props.getAdminClientSecret())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // Le token endpoint Keycloak est déduit du jwks-uri :
                // .../protocol/openid-connect/certs → .../protocol/openid-connect/token
                .tokenUri(props.getJwksUri().replace("/certs", "/token"))
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    /**
     * Manager OAuth2 limité au grant client_credentials (pas de session utilisateur).
     * Utilise {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} qui
     * fonctionne hors contexte de requête HTTP entrante.
     */
    @Bean
    public OAuth2AuthorizedClientManager keycloakAdminAuthorizedClientManager(
            ClientRegistrationRepository repo) {
        var clientService = new InMemoryOAuth2AuthorizedClientService(repo);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(repo, clientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());
        return manager;
    }

    /**
     * {@link RestClient} pré-configuré pour l'API Admin Keycloak.
     * Chaque requête reçoit automatiquement un header {@code Authorization: Bearer <token>}
     * obtenu via client_credentials.
     */
    @Bean
    public RestClient keycloakAdminRestClient(
            KeycloakProperties props,
            OAuth2AuthorizedClientManager clientManager) {
        return RestClient.builder()
                .baseUrl(props.getAdminApiBaseUrl())
                .requestInterceptor(bearerTokenInterceptor(clientManager))
                .build();
    }

    // ── Intercepteur Bearer token ─────────────────────────────────────────────

    private static org.springframework.http.client.ClientHttpRequestInterceptor
            bearerTokenInterceptor(OAuth2AuthorizedClientManager clientManager) {
        return (request, body, execution) -> {
            var authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(REGISTRATION_ID)
                    .principal(REGISTRATION_ID)   // principal fictif pour client_credentials
                    .build();

            var authorizedClient = clientManager.authorize(authorizeRequest);
            if (authorizedClient != null) {
                var token = authorizedClient.getAccessToken().getTokenValue();
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            return execution.execute(request, body);
        };
    }
}
