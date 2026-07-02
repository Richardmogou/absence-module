package com.banque.absences.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioProperties {

    /** URL du serveur MinIO — ex: http://minio:9000 */
    private String endpoint;

    /** Access key MinIO */
    private String accessKey;

    /** Secret key MinIO */
    private String secretKey;

    /** Nom du bucket de stockage des justificatifs */
    private String bucket = "justificatifs";
}
