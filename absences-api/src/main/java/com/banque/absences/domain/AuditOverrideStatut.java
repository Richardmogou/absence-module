package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trace d'un forçage de statut par ADMIN_RH via PATCH /{id}/statut (CDCT EX-9, §4.1.7-E).
 *
 * <p>Journal en ajout seul : aucune mise à jour ni suppression. Pas de relation JPA vers
 * {@link DemandeAbsence} — le lien est porté par {@code demandeId} pour que la trace survive
 * à la suppression de la demande.
 */
@Entity
@Table(name = "audit_override_statut")
@Getter
@Setter
@NoArgsConstructor
public class AuditOverrideStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "demande_id", nullable = false, updatable = false)
    private UUID demandeId;

    /** Identifiant Keycloak de l'ADMIN_RH auteur du forçage — le « qui ». */
    @Column(name = "auteur_identifiant_externe", nullable = false, updatable = false, length = 100)
    private String auteurIdentifiantExterne;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_ancien", nullable = false, updatable = false, length = 40)
    private StatutDemande statutAncien;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_nouveau", nullable = false, updatable = false, length = 40)
    private StatutDemande statutNouveau;

    @Column(nullable = false, updatable = false, length = 500)
    private String motif;

    @Column(name = "date_action", nullable = false, updatable = false)
    private LocalDateTime dateAction;
}
