package com.banque.absences.service;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DGConditionnelService {

    private final EtapeDemandeSnapshotRepository etapeDemandeSnapshotRepository;

    public boolean necessiteInjection(DemandeAbsence demande) {
        return demande.getType() == TypeAbsence.MISSION_LONGUE
                && !dejaInjecte(demande);
    }

    public void injecterEtapeConditionnelle(DemandeAbsence demande) {
        int position = demande.getPositionEtapeCourante() + 1;
        
        // Décaler les étapes existantes qui sont à cette position ou après
        etapeDemandeSnapshotRepository.shiftPositions(demande.getId(), position);

        EtapeDemandeSnapshot snap = new EtapeDemandeSnapshot();
        snap.setDemandeId(demande.getId());
        snap.setOrdre(position);
        snap.setPosition(position);
        snap.setLibelle("DG");
        snap.setMecanismeResolution(MecanismeResolution.DG_CONDITIONNEL);
        snap.setRoleHabilite("DG");
        snap.setVerrouille(false);
        etapeDemandeSnapshotRepository.save(snap);
        
        demande.setPositionEtapeCourante(position);
    }

    private boolean dejaInjecte(DemandeAbsence demande) {
        return etapeDemandeSnapshotRepository.findByDemandeIdAndMecanisme(
                demande.getId(), MecanismeResolution.DG_CONDITIONNEL).isPresent();
    }
}
