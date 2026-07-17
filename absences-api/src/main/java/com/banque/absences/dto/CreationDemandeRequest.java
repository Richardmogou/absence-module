package com.banque.absences.dto;

import com.banque.absences.domain.TypeAbsence;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * US-AGT-001 — Payload de création d'une demande d'absence.
 * RÈGLE : pas de champ gradeCode ni reseauId — ces données sont lues
 * depuis le JWT courant via {@link com.banque.absences.service.ClaimReaderService}.
 */
public record CreationDemandeRequest(

        @NotNull(message = "Le type d'absence est obligatoire")
        TypeAbsence type,

        /**
         * Date de début. Obligatoire pour tous les types SAUF {@code CONGE_MATERNITE} :
         * pour ce type, la date est fixée par l'analyste RH lors de l'instruction, puis
         * le système calcule automatiquement la date de fin (+14 semaines) et 98 jours.
         */
        LocalDate dateDebut,

        LocalDate dateFin,

        Integer nombreJours,

        String motifPermission,

        /** CONGE_ANNUEL uniquement — numéro de la fraction (1, 2, 3…). */
        Integer numeroFraction,

        /** CONGE_ANNUEL uniquement — true si c'est la première fraction de l'exercice. */
        Boolean estPremiereFraction,

        /** MISSION et MISSION_LONGUE uniquement — objet ou informations complémentaires de la mission. */
        String objetMission,

        /** MISSION et MISSION_LONGUE uniquement — justification ou motif détaillé de la mission. */
        String motifMission,

        /** MISSION et MISSION_LONGUE uniquement — ville ou pays de destination. */
        String destination,

        /** MISSION et MISSION_LONGUE uniquement — catégorie de la mission (ex: Terrain, Formation...). */
        String categorie,

        /**
         * Identifiant Keycloak du collègue Back-up désigné par le demandeur.
         * Obligatoire pour CONGE_ANNUEL (circuit Agent étape 1).
         * Stocké dans {@code DemandeAbsence.backupIdentifiantExterne}.
         */
        String backupIdentifiantExterne
) {}
