package com.banque.absences.dto;

import com.banque.absences.domain.JustificatifDocument;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AbsenceResponse {

    private UUID id;
    private String demandeurIdentifiantExterne;
    private String uniteIdentifiantExterne;
    @com.fasterxml.jackson.annotation.JsonProperty("type")
    private TypeAbsence typeAbsence;
    private StatutDemande statut;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Integer nombreJours;
    private Integer positionEtapeCourante;
    private String etapeCouranteLibelle;
    private String motifRejetSysteme;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** CONGE_ANNUEL uniquement — null pour les autres types. */
    private Integer numeroFraction;
    private Boolean estPremiereFraction;

    /** Nom complet du demandeur résolu via Keycloak Admin — null si indisponible. */
    private String nomCompletDemandeur;

    /** Back-up désigné par le demandeur — null si non renseigné. */
    private String backupIdentifiantExterne;

    /** Liste des justificatifs déposés pour cette demande. */
    private List<JustificatifDocument> justificatifs;

    /** URL du document de mise en congé généré après validation DRH. */
    private String documentMiseEnCongeUrl;

    /**
     * Vrai si l'utilisateur courant est le validateur pré-assigné à l'étape courante.
     * Calculé uniquement dans toResponse() (détail d'une demande), faux par défaut dans les listes.
     */
    private boolean estMonTourDeValider;

    /**
     * Progression du circuit de validation, calculée uniquement dans toResponse().
     * Null dans les réponses de liste.
     */
    private List<EtapeProgressionDto> progression;
}
