package com.banque.absences.dto;

/**
 * @param position index 0-based de l'étape dans le circuit
 * @param libelle  libellé affiché à l'utilisateur
 * @param statut   EN_ATTENTE | EN_COURS | APPROUVEE | REJETEE
 */
public record EtapeProgressionDto(int position, String libelle, String statut) {}
