package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "justificatif_document")
@Getter
@Setter
@NoArgsConstructor
public class JustificatifDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "demande_id", nullable = false)
    private UUID demandeId;

    @Column(name = "type_piece", nullable = false, length = 100)
    private String typePiece;

    @Column(name = "url_fichier", nullable = false, length = 500)
    private String urlFichier;

    @Column(name = "depose_le", nullable = false)
    private Instant deposeLe;
}
