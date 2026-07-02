package com.banque.absences.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "demande_permission")
@DiscriminatorValue("PERMISSION")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class DemandePermission extends DemandeAbsence {

    /** FK vers grille_permission.id — identifie le motif de la permission. */
    @Column(name = "evenement_id")
    private UUID evenementId;

    /** Code du motif (ex. "AUTRE_MOTIF") — dénormalisé pour éviter une jointure à la décision DRH. */
    @Column(name = "code_motif", length = 50)
    private String codeMotif;
}
