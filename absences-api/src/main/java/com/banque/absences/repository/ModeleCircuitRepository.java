package com.banque.absences.repository;

import com.banque.absences.domain.ModeleCircuit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModeleCircuitRepository extends JpaRepository<ModeleCircuit, UUID> {

    List<ModeleCircuit> findAllByActifTrue();

    @Query("SELECT DISTINCT m FROM ModeleCircuit m LEFT JOIN FETCH m.etapes e ORDER BY m.nom ASC")
    List<ModeleCircuit> findAllWithEtapes();

    @Query("SELECT m FROM ModeleCircuit m LEFT JOIN FETCH m.etapes e WHERE m.id = :id")
    Optional<ModeleCircuit> findByIdWithEtapes(UUID id);
}
