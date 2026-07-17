package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "validation")
@Getter
@Setter
@NoArgsConstructor
public class Validation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "demande_id", nullable = false)
    private UUID demandeId;

    @Column(name = "etape_snapshot_id", nullable = false)
    private UUID etapeSnapshotId;

    /** Identifiant Keycloak du validateur — PAS de relation JPA vers une entité Employé. */
    @Column(name = "validateur_identifiant_externe", nullable = false, length = 100)
    private String validateurIdentifiantExterne;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionValidation decision;

    @Column(length = 500)
    private String commentaire;

    @Column(name = "date_decision", nullable = false)
    private LocalDateTime dateDecision;

    /** Indique si cette décision a été prise par délégation. */
    @Column(name = "est_delegation", nullable = false)
    private boolean estDelegation = false;

    /** Identifiant Keycloak du déléguant (si estDelegation = true). */
    @Column(name = "delegant_identifiant_externe", length = 100)
    private String delegantIdentifiantExterne;

    public enum DecisionValidation {
        APPROUVEE,
        REJETEE,
        DELEGUEE,
        /**
         * Instruction du dossier par l'analyste RH — distincte d'APPROUVEE : l'analyste
         * renseigne la demande et la transmet, il ne se prononce pas sur son bien-fondé.
         */
        INSTRUITE
    }
}
