package com.banque.absences.repository;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.StatutDemande;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AbsenceRepository extends JpaRepository<DemandeAbsence, UUID> {

    List<DemandeAbsence> findByDemandeurIdentifiantExterne(String demandeurIdentifiantExterne);

    List<DemandeAbsence> findByStatut(StatutDemande statut);

    /**
     * Variante bornée pour les files de travail (/a-valider) : sous charge, les files
     * EN_INSTRUCTION_ANALYSTE_RH et EN_VALIDATION_DRH atteignent des milliers de lignes —
     * les charger toutes à chaque poll a mis l'API à genoux au test de performance
     * (p95 9,6 s sur /a-valider). FIFO : les plus anciennes d'abord.
     */
    List<DemandeAbsence> findByStatutOrderByCreatedAtAsc(StatutDemande statut, Pageable pageable);

    List<DemandeAbsence> findByDemandeurIdentifiantExterneAndStatut(
            String demandeurIdentifiantExterne, StatutDemande statut);

    List<DemandeAbsence> findByBackupIdentifiantExterne(String backupIdentifiantExterne);

    List<DemandeAbsence> findByBackupIdentifiantExterneAndStatut(
            String backupIdentifiantExterne, StatutDemande statut);

    /**
     * Retourne les demandes en attente de validation par un validateur donné.
     * Le validateur est pré-assigné à la soumission dans etape_demande_snapshot.validateur_identifiant_externe.
     */
    @Query("""
            SELECT DISTINCT d FROM DemandeAbsence d
            JOIN EtapeDemandeSnapshot e ON e.demandeId = d.id
            WHERE (e.validateurIdentifiantExterne = :validateurId
                   OR (e.mecanismeResolution IN (com.banque.absences.domain.MecanismeResolution.ROLE_FIXE_GLOBAL, com.banque.absences.domain.MecanismeResolution.DG_CONDITIONNEL)
                       AND CONCAT('ROLE_', e.roleHabilite) IN :roles)
                   OR (e.mecanismeResolution = com.banque.absences.domain.MecanismeResolution.ROLE_FIXE_SCOPE_RESEAU
                       AND CONCAT('ROLE_', e.roleHabilite) IN :roles
                       AND d.uniteIdentifiantExterne = :reseau))
              AND e.position = d.positionEtapeCourante
              AND d.statut = com.banque.absences.domain.StatutDemande.EN_VALIDATION_ETAPE
            ORDER BY d.createdAt ASC
            """)
    List<DemandeAbsence> findDemandesAValider(@Param("validateurId") String validateurId, @Param("roles") List<String> roles, @Param("reseau") String reseau, Pageable pageable);
}
