package com.banque.absences.repository;

import com.banque.absences.domain.RegleAffectation;
import com.banque.absences.domain.TypeAbsence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RegleAffectationRepository extends JpaRepository<RegleAffectation, UUID> {

    /** Priorité 1 : grade + type + unité spécifique. */
    @Query("""
            SELECT r FROM RegleAffectation r
            JOIN FETCH r.etapeModeleCircuit e
            JOIN FETCH e.modeleCircuit m
            WHERE r.gradeDeclencheur = :grade
              AND m.typeAbsenceCible  = :type
              AND m.uniteIdentifianteExterne = :unite
            ORDER BY r.priorite ASC
            """)
    List<RegleAffectation> findByGradeAndTypeAndUnite(
            @Param("grade") String grade,
            @Param("type")  TypeAbsence type,
            @Param("unite") String unite);

    /** Priorité 2 : grade + type, circuit global (toutes unités). */
    @Query("""
            SELECT r FROM RegleAffectation r
            JOIN FETCH r.etapeModeleCircuit e
            JOIN FETCH e.modeleCircuit m
            WHERE r.gradeDeclencheur = :grade
              AND m.typeAbsenceCible  = :type
              AND m.uniteIdentifianteExterne IS NULL
            ORDER BY r.priorite ASC
            """)
    List<RegleAffectation> findByGradeAndTypeGlobal(
            @Param("grade") String grade,
            @Param("type")  TypeAbsence type);

    /** Fallback : grade uniquement, circuit global. */
    @Query("""
            SELECT r FROM RegleAffectation r
            JOIN FETCH r.etapeModeleCircuit e
            JOIN FETCH e.modeleCircuit m
            WHERE r.gradeDeclencheur = :grade
              AND m.uniteIdentifianteExterne IS NULL
            ORDER BY r.priorite ASC
            """)
    List<RegleAffectation> findByGradeGlobal(@Param("grade") String grade);
}
