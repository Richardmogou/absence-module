package com.banque.absences.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Configuration de test (profil {@code test}) : remplace le {@link S3Client} réel
 * par un mock Mockito.
 *
 * <p>Le bean {@code s3Client} de {@link AppConfig} est injecté dans
 * {@code MinioStorageService}, dont le {@code @PostConstruct} appelle MinIO au
 * démarrage (création de bucket + politique). Sans MinIO réel accessible avec des
 * identifiants valides, ce {@code init()} échoue et fait tomber tout le contexte
 * Spring — bloquant l'ensemble des tests d'intégration {@code @SpringBootTest}.
 *
 * <p>Étant sous {@code src/test}, cette configuration n'affecte jamais la production.
 * Elle est prise en compte par component-scan lors des {@code @SpringBootTest}.
 */
@Configuration
@Profile("test")
public class TestMinioConfig {

    @Bean
    @Primary
    S3Client mockS3Client() {
        return Mockito.mock(S3Client.class);
    }
}
