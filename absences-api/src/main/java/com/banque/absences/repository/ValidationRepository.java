package com.banque.absences.repository;

import com.banque.absences.domain.Validation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationRepository extends JpaRepository<Validation, UUID> {
    List<Validation> findByDemandeId(UUID demandeId);
}
