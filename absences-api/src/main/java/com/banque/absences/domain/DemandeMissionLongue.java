package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "demande_mission_longue")
@DiscriminatorValue("MISSION_LONGUE")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class DemandeMissionLongue extends DemandeAbsence {

    @Column(name = "objet_mission", length = 500)
    private String objetMission;

    @Column(name = "motif_mission", length = 1000)
    private String motifMission;

    @Column(name = "destination", length = 200)
    private String destination;

    @Column(name = "categorie", length = 100)
    private String categorie;
}
