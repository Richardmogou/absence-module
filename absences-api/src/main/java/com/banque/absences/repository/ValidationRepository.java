package com.banque.absences.repository;

import com.banque.absences.domain.Validation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationRepository extends JpaRepository<Validation, UUID> {

    List<Validation> findByDemandeId(UUID demandeId);

    /**
     * Journal des décisions d'une demande, dans l'ordre chronologique.
     *
     * <p>À préférer à {@link #findByDemandeId} dès qu'il s'agit de restituer l'historique :
     * sans ORDER BY, l'ordre des lignes dépend du plan d'exécution PostgreSQL — un journal
     * d'audit ne peut pas en dépendre.
     */
    List<Validation> findByDemandeIdOrderByDateDecisionAsc(UUID demandeId);
}
