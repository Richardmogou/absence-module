package com.banque.absences.repository;

import com.banque.absences.domain.DocumentMiseEnConge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentMiseEnCongeRepository extends JpaRepository<DocumentMiseEnConge, UUID> {

    boolean existsByDemandeId(UUID demandeId);

    Optional<DocumentMiseEnConge> findByDemandeId(UUID demandeId);

    java.util.List<DocumentMiseEnConge> findAllByDemandeIdIn(java.util.List<UUID> demandeIds);
}
