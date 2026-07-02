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

        @NotNull(message = "La date de début est obligatoire")
        LocalDate dateDebut,

        LocalDate dateFin,

        Integer nombreJours,

        String motifPermission,

        /** CONGE_ANNUEL uniquement — numéro de la fraction (1, 2, 3…). */
        Integer numeroFraction,

        /** CONGE_ANNUEL uniquement — true si c'est la première fraction de l'exercice. */
        Boolean estPremiereFraction,

        /** MISSION_LONGUE uniquement — objet ou informations complémentaires de la mission. */
        String objetMission,

        /**
         * Identifiant Keycloak du collègue Back-up désigné par le demandeur.
         * Obligatoire pour CONGE_ANNUEL (circuit Agent étape 1).
         * Stocké dans {@code DemandeAbsence.backupIdentifiantExterne}.
         */
        String backupIdentifiantExterne
) {}
