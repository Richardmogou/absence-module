package com.banque.absences.repository;

import com.banque.absences.domain.SoldeConge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SoldeCongeRepository extends JpaRepository<SoldeConge, UUID> {

    Optional<SoldeConge> findByEmployeIdentifiantExterneAndExercice(
            String employeIdentifiantExterne, int exercice);

    @Modifying
    @Query("UPDATE SoldeConge s SET s.joursPris = s.joursPris - :jours " +
           "WHERE s.employeIdentifiantExterne = :employeId " +
           "AND s.exercice = :exercice")
    int recrediterJours(@Param("employeId") String employeId,
                        @Param("jours") int jours,
                        @Param("exercice") int exercice);

    @Modifying
    @Query("UPDATE SoldeConge s SET s.joursPris = s.joursPris + :jours, " +
           "s.joursRestants = s.joursRestants - :jours " +
           "WHERE s.employeIdentifiantExterne = :employeId " +
           "AND s.exercice = :exercice")
    int debiterJours(@Param("employeId") String employeId,
                     @Param("jours") int jours,
                     @Param("exercice") int exercice);
}
