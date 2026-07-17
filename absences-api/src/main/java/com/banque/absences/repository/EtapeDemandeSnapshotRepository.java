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
     * Exclut l'instruction RH et la validation DRH, traitées séparément après le circuit.
     *
     * <p>L'exclusion porte sur le RÔLE, pas sur le mécanisme. Le filtre historique excluait
     * {@code ROLE_FIXE_GLOBAL} au motif que ce mécanisme désignait « Analyste RH et DRH » — un
     * raccourci vrai à l'origine, devenu faux quand V14 a implémenté la matrice d'approbation
     * des missions : elle y déclare « Autorisation Directeur Général » et « Approbation PCA »
     * en ROLE_FIXE_GLOBAL. Ces étapes étaient donc exclues du circuit et jamais sollicitées —
     * une mission internationale partait en instruction RH sans l'autorisation du DG, et la
     * mission d'un DG sans aucune approbation du PCA, sa seule étape d'approbation ayant
     * disparu du flux.
     */
    @Query("""
            SELECT e FROM EtapeDemandeSnapshot e
            WHERE e.demandeId = :demandeId
              AND (e.roleHabilite IS NULL
               OR e.roleHabilite NOT IN ('ANALYSTE_RH', 'DRH'))
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
