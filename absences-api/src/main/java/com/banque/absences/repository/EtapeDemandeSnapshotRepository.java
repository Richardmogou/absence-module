package com.banque.absences.repository;

import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.MecanismeResolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtapeDemandeSnapshotRepository extends JpaRepository<EtapeDemandeSnapshot, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE EtapeDemandeSnapshot e SET e.position = e.position + 1, e.ordre = e.ordre + 1 WHERE e.demandeId = :demandeId AND e.position >= :position")
    void shiftPositions(@Param("demandeId") UUID demandeId, @Param("position") int position);

    /**
     * Retourne les étapes intermédiaires d'une demande, ordonnées par position croissante.
     * Exclut les étapes de type ROLE_FIXE_GLOBAL (Analyste RH et DRH)
     * qui sont traitées séparément dans le workflow.
     */
    @Query("""
            SELECT e FROM EtapeDemandeSnapshot e
            WHERE e.demandeId = :demandeId
              AND (e.mecanismeResolution IS NULL
               OR e.mecanismeResolution NOT IN
                  (com.banque.absences.domain.MecanismeResolution.ROLE_FIXE_GLOBAL))
            ORDER BY e.ordre ASC
            """)
    List<EtapeDemandeSnapshot> findIntermediairesOrdonnees(@Param("demandeId") UUID demandeId);

    List<EtapeDemandeSnapshot> findByDemandeIdOrderByOrdreAsc(UUID demandeId);

    List<EtapeDemandeSnapshot> findByDemandeIdIn(List<UUID> demandeIds);

    /**
     * Retourne l'étape dont la position (ordre) correspond à la position courante de la demande.
     */
    @Query("""
            SELECT e FROM EtapeDemandeSnapshot e
            WHERE e.demandeId = :demandeId
              AND e.position  = :position
            """)
    java.util.Optional<EtapeDemandeSnapshot> findByDemandeIdAndPosition(
            @Param("demandeId") UUID demandeId, @Param("position") int position);

    @Query("""
            SELECT e FROM EtapeDemandeSnapshot e
            WHERE e.demandeId = :demandeId
              AND e.mecanismeResolution = :mecanisme
            """)
    Optional<EtapeDemandeSnapshot> findByDemandeIdAndMecanisme(
            @Param("demandeId") UUID demandeId,
            @Param("mecanisme") MecanismeResolution mecanisme);
}
