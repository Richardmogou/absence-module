package com.banque.absences.service;

import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.JustificatifDocument;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.dto.AbsenceRequest;
import com.banque.absences.dto.AbsenceResponse;
import com.banque.absences.dto.CreationDemandeRequest;
import com.banque.absences.dto.CreationProlongationRequest;
import com.banque.absences.dto.DepotJustificatifRequest;
import com.banque.absences.dto.ModificationDemandeRequest;
import com.banque.absences.dto.PreviewDemandeResponse;
import com.banque.absences.dto.SoldeCongeResponse;
import com.banque.absences.dto.StatutUpdateRequest;
import com.banque.absences.dto.ValidationEtapeRequest.Decision;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AbsenceService {

    DemandeAbsence creerDemande(CreationDemandeRequest request);

    DemandeAbsence soumettre(UUID demandeId, boolean confirmDoublon);

    PreviewDemandeResponse previsualiser(UUID demandeId);

    DemandeAbsence enregistrerDecisionEtape(UUID demandeId, Decision decision, String motif);

    AbsenceResponse creer(AbsenceRequest request);

    AbsenceResponse findById(UUID id);

    List<AbsenceResponse> findAll();

    List<AbsenceResponse> findByStatut(StatutDemande statut);

    List<AbsenceResponse> findByDemandeur(String demandeurIdentifiantExterne);

    List<AbsenceResponse> findByDemandeurAndStatut(String demandeurIdentifiantExterne, StatutDemande statut);

    List<AbsenceResponse> findByBackup(String backupIdentifiantExterne);

    List<AbsenceResponse> findDemandesAValider();

    SoldeCongeResponse findSoldeByDemandeur(String demandeurIdentifiantExterne);

    AbsenceResponse updateStatut(UUID id, StatutUpdateRequest request);

    DemandeAbsence modifier(UUID demandeId, ModificationDemandeRequest dto);

    void supprimer(UUID id);

    JustificatifDocument deposerJustificatif(UUID demandeId, String typePiece,
                                              org.springframework.web.multipart.MultipartFile fichier);

    DemandeAbsence enregistrerDecisionDRH(UUID demandeId, Decision decision, String motif,
                                          Integer nombreJoursAjuste);

    DemandeAbsence creerProlongation(UUID demandeInitialeId, CreationProlongationRequest dto);

    DemandeAbsence retourAnticipe(UUID demandeId, LocalDate dateRetourEffective);
}
