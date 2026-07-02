package com.banque.absences.repository;

import com.banque.absences.domain.EtapeModeleCircuit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EtapeModeleCircuitRepository extends JpaRepository<EtapeModeleCircuit, UUID> {

    List<EtapeModeleCircuit> findByModeleCircuitIdOrderByOrdreAsc(UUID modeleCircuitId);
}
