package com.banque.absences.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "regle_affectation")
@Getter
@Setter
@NoArgsConstructor
public class RegleAffectation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "etape_modele_circuit_id", nullable = false)
    private EtapeModeleCircuit etapeModeleCircuit;

    @Enumerated(EnumType.STRING)
    @Column(name = "mecanisme", nullable = false, length = 30)
    private MecanismeResolution mecanisme;

    /** Profondeur hiérarchique (utilisée pour HIERARCHIQUE). */
    @Column(name = "profondeur_hierarchique")
    private Integer profondeurHierarchique;

    /** Rôle Keycloak cible (utilisé pour ROLE_FIXE_*). */
    @Column(name = "role_keycloak_cible", length = 80)
    private String roleKeycloakCible;

    /** Grade déclencheur (utilisé pour DG_CONDITIONNEL). */
    @Column(name = "grade_declencheur", length = 50)
    private String gradeDeclencheur;

    @Column(nullable = false)
    private Integer priorite = 1;

    /**
     * Remonte au {@link ModeleCircuit} propriétaire de l'étape portant cette règle.
     * Requiert que {@code etapeModeleCircuit} et son {@code modeleCircuit} soient chargés
     * (garanti par la requête FETCH de {@link com.banque.absences.repository.RegleAffectationRepository}).
     */
    @JsonIgnore
    public ModeleCircuit getCircuit() {
        return etapeModeleCircuit.getModeleCircuit();
    }
}
