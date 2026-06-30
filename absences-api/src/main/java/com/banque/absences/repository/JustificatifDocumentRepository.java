package com.banque.absences.repository;

import com.banque.absences.domain.JustificatifDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface JustificatifDocumentRepository extends JpaRepository<JustificatifDocument, UUID> {

    boolean existsByDemandeId(UUID demandeId);

    List<JustificatifDocument> findAllByDemandeId(UUID demandeId);

    List<JustificatifDocument> findAllByDemandeIdIn(Collection<UUID> demandeIds);
}
