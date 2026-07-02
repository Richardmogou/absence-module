package com.banque.absences.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak")
@Data
public class KeycloakProperties {

    private String jwksUri;
    private String adminApiBaseUrl;
    private String adminClientId;
    private String adminClientSecret;
}
