package com.banque.absences.domain;

import com.banque.absences.domain.MecanismeResolution;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "etape_demande_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class EtapeDemandeSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "demande_id", nullable = false)
    private UUID demandeId;

    @Column(nullable = false)
    private Integer ordre;

    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    /** Identifiant Keycloak du validateur affecté — PAS de relation JPA vers Employé. */
    @Column(name = "validateur_identifiant_externe", length = 100)
    private String validateurIdentifiantExterne;

    /** Mécanisme d'habilitation pour cette étape (copié depuis RegleAffectation au moment de la soumission). */
    @Enumerated(EnumType.STRING)
    @Column(name = "mecanisme_resolution", length = 30)
    private MecanismeResolution mecanismeResolution;

    @Column(name = "role_habilite", length = 80)
    private String roleHabilite;

    /** Position (= ordre) de l'étape dans le circuit — correspond à positionEtapeCourante de la demande. */
    @Column(name = "position")
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutEtape statut = StatutEtape.EN_ATTENTE;

    @Column(name = "verrouille", nullable = false)
    private boolean verrouille = false;

    @Column(name = "date_affectation")
    private LocalDateTime dateAffectation;

    @Column(name = "date_limite")
    private LocalDateTime dateLimite;

    @Column(name = "date_traitement")
    private LocalDateTime dateTraitement;

    public enum StatutEtape {
        EN_ATTENTE, EN_COURS, VALIDEE, REJETEE, DELEGUEE, IGNOREE
    }
}
