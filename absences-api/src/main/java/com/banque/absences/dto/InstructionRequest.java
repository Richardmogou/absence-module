package com.banque.absences.dto;

import java.time.LocalDate;

/**
 * Payload optionnel de l'instruction par l'analyste RH.
 * {@code dateDebut} n'est utilisé que pour le CONGE_MATERNITE : l'analyste RH fixe
 * la date de début, le système calcule ensuite la date de fin (+14 semaines) et 98 jours.
 */
public record InstructionRequest(LocalDate dateDebut) {}
