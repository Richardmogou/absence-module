package com.banque.absences.service;

import com.banque.absences.config.MinioProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinioProperties minioProperties;
    private final S3Client s3Client;

    @PostConstruct
    void init() {
        creerBucketSiAbsent();
        appliquerPolitiqueLecturePublique();
    }

    /**
     * Upload un fichier vers MinIO et retourne son URL publique.
     */
    public String uploader(MultipartFile fichier, String typePiece, UUID demandeId) {
        String extension = extraireExtension(fichier.getOriginalFilename());
        String objectKey = demandeId + "/" + typePiece + "_" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(minioProperties.getBucket())
                    .key(objectKey)
                    .contentType(fichier.getContentType())
                    .contentLength(fichier.getSize())
                    .build();
            s3Client.putObject(req, RequestBody.fromInputStream(
                    fichier.getInputStream(), fichier.getSize()));
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload du fichier vers MinIO", e);
        }

        return minioProperties.getEndpoint()
                + "/" + minioProperties.getBucket()
                + "/" + objectKey;
    }

    private void creerBucketSiAbsent() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(minioProperties.getBucket())
                    .build());
            log.info("Bucket MinIO '{}' déjà existant", minioProperties.getBucket());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(minioProperties.getBucket())
                    .build());
            log.info("Bucket MinIO '{}' créé avec succès", minioProperties.getBucket());
        }
    }

    private String extraireExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
    private void appliquerPolitiqueLecturePublique() {
        try {
            String policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": "*",
                            "Action": ["s3:GetObject"],
                            "Resource": ["arn:aws:s3:::%s/*"]
                        }
                    ]
                }
                """.formatted(minioProperties.getBucket());

            s3Client.putBucketPolicy(software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest.builder()
                    .bucket(minioProperties.getBucket())
                    .policy(policy)
                    .build());
            log.info("Politique publique appliquée au bucket '{}'", minioProperties.getBucket());
        } catch (Exception e) {
            log.warn("Impossible d'appliquer la politique publique au bucket '{}': {}", minioProperties.getBucket(), e.getMessage());
        }
    }
}
