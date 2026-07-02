package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "grille_permission")
@Getter
@Setter
@NoArgsConstructor
public class GrillePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code_motif", nullable = false, unique = true, length = 50)
    private String codeMotif;

    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @Column(name = "duree_jours", nullable = false)
    private Integer dureeJours;

    @Column(name = "justificatif_requis", nullable = false)
    private boolean justificatifRequis = false;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
