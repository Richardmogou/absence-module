package com.banque.absences.domain;

/**
 * Événements pilotant la machine à états des demandes d'absence.
 * Chaque événement déclenche une transition de {@link StatutDemande}.
 */
public enum AbsenceEvent {
    SOUMETTRE,
    VALIDER,
    REJETER,
    TRANSMETTRE,
    ACTIVER_DELEGATION,
    REVOQUER_DELEGATION,
    REJETER_SYSTEME,
    ANNULER
}
