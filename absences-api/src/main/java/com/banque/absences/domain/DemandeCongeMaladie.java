package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "demande_conge_maladie")
@DiscriminatorValue("CONGE_MALADIE")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class DemandeCongeMaladie extends DemandeAbsence {
}
