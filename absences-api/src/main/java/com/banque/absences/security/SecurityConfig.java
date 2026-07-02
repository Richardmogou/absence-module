package com.banque.absences.security;

import com.banque.absences.config.KeycloakProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final KeycloakProperties keycloakProperties;
    private final JetonInvalideEntryPoint jetonInvalideEntryPoint;
    private final KeycloakJwtConverter keycloakJwtConverter;

    public SecurityConfig(KeycloakProperties keycloakProperties,
                          JetonInvalideEntryPoint jetonInvalideEntryPoint,
                          KeycloakJwtConverter keycloakJwtConverter) {
        this.keycloakProperties       = keycloakProperties;
        this.jetonInvalideEntryPoint  = jetonInvalideEntryPoint;
        this.keycloakJwtConverter     = keycloakJwtConverter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v5/admin/**").hasRole("ADMIN_RH")
                .requestMatchers("/api/v5/referentiel/**").authenticated()
                // Endpoints Analyste RH
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/v5/demandes/*/instruction").hasRole("ANALYSTE_RH")
                // Endpoints DRH
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/v5/demandes/*/validation-drh").hasRole("DRH")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwkSetUri(keycloakProperties.getJwksUri())
                    .jwtAuthenticationConverter(keycloakJwtConverter)
                )
                .authenticationEntryPoint(jetonInvalideEntryPoint)   // couvre token expiré / signature invalide
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jetonInvalideEntryPoint)   // couvre absence de token
            );
        return http.build();
    }
}
