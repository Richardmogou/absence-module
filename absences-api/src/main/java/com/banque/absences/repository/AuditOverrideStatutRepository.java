package com.banque.absences.repository;

import com.banque.absences.domain.AuditOverrideStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditOverrideStatutRepository extends JpaRepository<AuditOverrideStatut, UUID> {

    /** Ordre chronologique explicite : un journal d'audit ne peut pas dépendre de l'ordre physique des lignes. */
    List<AuditOverrideStatut> findByDemandeIdOrderByDateActionAsc(UUID demandeId);
}
