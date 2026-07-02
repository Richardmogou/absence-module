package com.banque.absences.repository;

import com.banque.absences.domain.GrillePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GrillePermissionRepository extends JpaRepository<GrillePermission, UUID> {
    List<GrillePermission> findByActifTrue();
    Optional<GrillePermission> findByCodeMotif(String codeMotif);
}
