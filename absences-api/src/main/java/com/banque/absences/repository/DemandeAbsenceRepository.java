package com.banque.absences.repository;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.TypeAbsence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandeAbsenceRepository extends JpaRepository<DemandeAbsence, UUID> {

    List<DemandeAbsence> findByDemandeurIdentifiantExterneAndType(
            String demandeurIdentifiantExterne, TypeAbsence type);
            
    List<DemandeAbsence> findByDemandeurIdentifiantExterne(String demandeurIdentifiantExterne);

    List<DemandeAbsence> findByStatutIn(java.util.Collection<com.banque.absences.domain.StatutDemande> statuts);

    List<DemandeAbsence> findByDemandeurIdentifiantExterneAndStatutNotIn(
            String demandeurIdentifiantExterne, java.util.Collection<com.banque.absences.domain.StatutDemande> statuts);
}
