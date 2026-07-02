package com.banque.absences.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * Payload de dépôt de justificatif.
 * Le fichier est transmis en multipart/form-data.
 * urlFichier est calculé par le backend après upload vers MinIO.
 */
public record DepotJustificatifRequest(String typePiece, MultipartFile fichier) {}
