package com.banque.absences.service;

import com.banque.absences.domain.GrillePermission;
import com.banque.absences.repository.GrillePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BaremePermissionService {

    private final GrillePermissionRepository grillePermissionRepository;

    @Cacheable("baremePermission")
    public List<GrillePermission> listerMotifs() {
        return grillePermissionRepository.findByActifTrue();
    }

    /**
     * Retourne la durée réglementaire associée au motif.
     * -1 indique motif AUTRES : la durée saisie par le client est conservée.
     */
    public int appliquerBareme(String codeMotif) {
        if ("AUTRES".equals(codeMotif)) {
            return -1;
        }
        return grillePermissionRepository.findByCodeMotif(codeMotif)
                .map(GrillePermission::getDureeJours)
                .orElseThrow(() -> new MotifInconnuException(
                        "Motif de permission non reconnu : " + codeMotif));
    }
}
