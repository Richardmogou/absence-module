package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "modele_circuit")
@Getter
@Setter
@NoArgsConstructor
public class ModeleCircuit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_absence_cible", length = 30)
    private TypeAbsence typeAbsenceCible;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "est_modele_nomme", nullable = false)
    private boolean estModeleNomme = false;

    @Column(name = "unite_identifiante_externe", length = 100)
    private String uniteIdentifianteExterne;

    @OneToMany(mappedBy = "modeleCircuit", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordre ASC")
    private List<EtapeModeleCircuit> etapes = new ArrayList<>();
}
