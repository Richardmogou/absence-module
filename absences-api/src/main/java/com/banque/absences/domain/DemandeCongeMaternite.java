package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "demande_conge_maternite")
@DiscriminatorValue("CONGE_MATERNITE")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class DemandeCongeMaternite extends DemandeAbsence {

    @Column(name = "est_prolongation")
    private Boolean estProlongation;

    /** Référence vers la demande initiale en cas de prolongation — PAS de relation JPA cyclique. */
    @Column(name = "demande_initiale_id")
    private UUID demandeInitialeId;
}
