package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "demande_absence")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 30)
@Getter
@Setter
@NoArgsConstructor
public class DemandeAbsence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Identifiant Keycloak de l'employé demandeur — PAS de relation JPA vers une entité Employé. */
    @Column(name = "demandeur_identifiant_externe", nullable = false, length = 100)
    private String demandeurIdentifiantExterne;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", insertable = false, updatable = false, length = 30)
    private TypeAbsence type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatutDemande statut = StatutDemande.BROUILLON;

    @Column(name = "circuit_id")
    private UUID circuitId;

    @Column(name = "circuit_nom", length = 100)
    private String circuitNom;

    @Column(name = "position_etape_courante")
    private Integer positionEtapeCourante;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "nombre_jours")
    private Integer nombreJours;

    /** Identifiant Keycloak de l'unité organisationnelle — PAS de relation JPA vers une entité Unité. */
    @Column(name = "unite_identifiant_externe", nullable = false, length = 100)
    private String uniteIdentifiantExterne;

    @Column(name = "backup_identifiant_externe", length = 100)
    private String backupIdentifiantExterne;

    @Column(name = "motif_rejet_systeme", length = 500)
    private String motifRejetSysteme;

    @Column(name = "doublon_confirme", nullable = false)
    private boolean doublonConfirme = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.statut == null) this.statut = StatutDemande.BROUILLON;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
