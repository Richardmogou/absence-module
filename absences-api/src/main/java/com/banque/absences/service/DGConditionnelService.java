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

    private static final String ROLE_DG = "DG";

    private final EtapeDemandeSnapshotRepository etapeDemandeSnapshotRepository;

    /**
     * Le DG n'est sollicité en étape conditionnelle que si le circuit ne le prévoit pas déjà.
     *
     * <p>Sans le contrôle {@link #circuitComporteDejaEtapeDG}, une mission longue empruntant un
     * circuit qui route déjà vers le DG (Circuit Manager, Circuit Réseau) recevait une SECONDE
     * étape DG : le directeur général validait deux fois la même demande. C'est le doublon de
     * validateur que {@code CircuitCoherenceCheckerService} interdit à la configuration des
     * circuits — ici il était fabriqué par le système en cours de circuit.
     *
     * <p>Portée : un circuit dont l'étape DG est purement HIERARCHIQUE et sans
     * {@code roleHabilite} renseigné n'est pas détecté — le rôle n'y est résolu qu'à
     * l'exécution via Keycloak.
     */
    public boolean necessiteInjection(DemandeAbsence demande) {
        return demande.getType() == TypeAbsence.MISSION_LONGUE
                && demande.getNombreJours() != null
                && demande.getNombreJours() >= 15
                && !circuitComporteDejaEtapeDG(demande)
                && !dejaInjecte(demande);
    }

    /** Vrai si une étape du circuit désigne déjà le DG, quel que soit son mécanisme. */
    private boolean circuitComporteDejaEtapeDG(DemandeAbsence demande) {
        return etapeDemandeSnapshotRepository
                .findByDemandeIdOrderByOrdreAsc(demande.getId())
                .stream()
                .anyMatch(e -> ROLE_DG.equals(e.getRoleHabilite()));
    }

    public void injecterEtapeConditionnelle(DemandeAbsence demande) {
        int position = demande.getPositionEtapeCourante() + 1;
        
        // Décaler les étapes existantes qui sont à cette position ou après
        etapeDemandeSnapshotRepository.shiftPositions(demande.getId(), position);

        EtapeDemandeSnapshot snap = new EtapeDemandeSnapshot();
        snap.setDemandeId(demande.getId());
        snap.setOrdre(position);
        snap.setPosition(position);
        snap.setLibelle(ROLE_DG);
        snap.setMecanismeResolution(MecanismeResolution.DG_CONDITIONNEL);
        snap.setRoleHabilite(ROLE_DG);
        snap.setVerrouille(false);
        etapeDemandeSnapshotRepository.save(snap);
        
        demande.setPositionEtapeCourante(position);
    }

    private boolean dejaInjecte(DemandeAbsence demande) {
        return etapeDemandeSnapshotRepository.findByDemandeIdAndMecanisme(
                demande.getId(), MecanismeResolution.DG_CONDITIONNEL).isPresent();
    }
}
