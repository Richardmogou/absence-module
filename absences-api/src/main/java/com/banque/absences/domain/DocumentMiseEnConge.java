package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_mise_en_conge")
@Getter
@Setter
@NoArgsConstructor
public class DocumentMiseEnConge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "demande_id", nullable = false)
    private UUID demandeId;

    @Column(name = "numero", nullable = false, unique = true, length = 50)
    private String numero;

    @Column(name = "url_document", nullable = false, length = 500)
    private String urlDocument;

    @Column(name = "genere_le", nullable = false)
    private Instant genereLe;
}
