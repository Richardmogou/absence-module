package com.banque.absences.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "etape_modele_circuit")
@Getter
@Setter
@NoArgsConstructor
public class EtapeModeleCircuit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modele_circuit_id", nullable = false)
    private ModeleCircuit modeleCircuit;

    @Column(nullable = false)
    private Integer ordre;

    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;

    @Column(name = "delai_jours")
    private Integer delaiJours;

    @Column(name = "est_verrouillable", nullable = false)
    private boolean estVerrouillable = false;

    @OneToMany(mappedBy = "etapeModeleCircuit", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RegleAffectation> regles = new ArrayList<>();
}
