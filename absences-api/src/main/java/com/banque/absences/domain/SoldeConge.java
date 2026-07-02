package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "solde_conge",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_solde_employe_exercice",
               columnNames = {"employe_identifiant_externe", "exercice"}))
@Getter
@Setter
@NoArgsConstructor
public class SoldeConge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Identifiant Keycloak de l'employé — PAS de relation JPA vers une entité Employé. */
    @Column(name = "employe_identifiant_externe", nullable = false, length = 100)
    private String employeIdentifiantExterne;

    @Column(nullable = false)
    private Integer exercice;

    @Column(name = "jours_acquis", nullable = false)
    private Integer joursAcquis = 0;

    @Column(name = "jours_pris", nullable = false)
    private Integer joursPris = 0;

    @Column(name = "jours_restants", nullable = false)
    private Integer joursRestants = 0;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PreUpdate
    void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
