package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "demande_conge_annuel")
@DiscriminatorValue("CONGE_ANNUEL")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class DemandeCongeAnnuel extends DemandeAbsence {

    @Column(name = "numero_fraction")
    private Integer numeroFraction;

    @Column(name = "est_premiere_fraction")
    private Boolean estPremiereFraction;
}
