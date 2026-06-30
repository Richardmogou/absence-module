package com.banque.absences.service;

import com.banque.absences.domain.AbsenceEvent;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.DemandeCongeAnnuel;
import com.banque.absences.domain.DemandeMissionLongue;
import com.banque.absences.domain.DemandeCongeMaternite;
import com.banque.absences.domain.DemandePermission;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.EtapeModeleCircuit;
import com.banque.absences.domain.ModeleCircuit;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.domain.Validation;
import com.banque.absences.domain.JustificatifDocument;
import com.banque.absences.dto.AbsenceRequest;
import com.banque.absences.dto.AbsenceResponse;
import com.banque.absences.dto.CreationDemandeRequest;
import com.banque.absences.dto.CreationProlongationRequest;
import com.banque.absences.dto.DepotJustificatifRequest;
import com.banque.absences.dto.ModificationDemandeRequest;
import com.banque.absences.dto.PreviewDemandeResponse;
import com.banque.absences.dto.StatutUpdateRequest;
import com.banque.absences.dto.ValidationEtapeRequest.Decision;
import com.banque.absences.exception.CircuitNonDetermineException;
import com.banque.absences.exception.DoublonDetecteException;
import com.banque.absences.repository.AbsenceRepository;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.repository.EtapeModeleCircuitRepository;
import com.banque.absences.repository.JustificatifDocumentRepository;
import com.banque.absences.repository.ValidationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbsenceServiceImpl implements AbsenceService {

    private static final Set<TypeAbsence> TYPES_AVEC_JUSTIFICATIF = Set.of(
            TypeAbsence.CONGE_MALADIE,
            TypeAbsence.PERMISSION,
            TypeAbsence.MISSION_LONGUE,
            TypeAbsence.CONGE_MATERNITE
    );

    private final AbsenceRepository repository;
    private final DemandeAbsenceRepository demandeAbsenceRepository;
    private final EtapeModeleCircuitRepository etapeModeleCircuitRepository;
    private final EtapeDemandeSnapshotRepository etapeDemandeSnapshotRepository;
    private final ValidationRepository validationRepository;
    private final JustificatifDocumentRepository justificatifDocumentRepository;
    private final DocumentMiseEnCongeService documentMiseEnCongeService;
    private final BaremePermissionService baremePermissionService;
    private final ClaimReaderService claimReaderService;
    private final DoublonDetectionService doublonDetectionService;
    private final CircuitDeterminationService circuitDeterminationService;
    private final AbsenceStateMachine stateMachine;
    private final HierarchicalChainResolver hierarchicalChainResolver;
    private final SoldeCongeService soldeCongeService;
    private final SystemeHabilitations systemeHabilitations;
    private final MinioStorageService minioStorageService;

    @Override
    @Transactional
    public DemandeAbsence creerDemande(CreationDemandeRequest dto) {
        String demandeurId = claimReaderService.identifiantUtilisateurCourant();

        // US-MLG-001 — Mission longue : durée intrinsèque >= 15 jours
        if (dto.type() == TypeAbsence.MISSION_LONGUE) {
            if (dto.nombreJours() == null || dto.nombreJours() < 15) {
                throw new DureeInsuffisanteMissionLongueException(
                        "Une Mission longue doit avoir une duree d'au moins 15 jours");
            }
            DemandeMissionLongue demande = new DemandeMissionLongue();
            demande.setDemandeurIdentifiantExterne(demandeurId);
            demande.setUniteIdentifiantExterne(claimReaderService.lireClaimReseau().orElse(demandeurId));
            demande.setType(TypeAbsence.MISSION_LONGUE);
            demande.setDateDebut(dto.dateDebut());
            demande.setDateFin(dto.dateFin() != null
                    ? dto.dateFin()
                    : dto.dateDebut().plusDays(dto.nombreJours() - 1));
            demande.setNombreJours(dto.nombreJours());
            demande.setObjetMission(dto.objetMission());
            demande.setStatut(StatutDemande.BROUILLON);
            demande.setCircuitId(null);
            return demandeAbsenceRepository.save(demande);
        }

        if (dto.type() == TypeAbsence.PERMISSION) {
            int duree = baremePermissionService.appliquerBareme(dto.motifPermission());
            DemandePermission demande = new DemandePermission();
            demande.setDemandeurIdentifiantExterne(demandeurId);
            demande.setUniteIdentifiantExterne(claimReaderService.lireClaimReseau().orElse(demandeurId));
            demande.setType(TypeAbsence.PERMISSION);
            demande.setDateDebut(dto.dateDebut());
            demande.setDateFin(dto.dateFin());
            demande.setNombreJours(duree == -1 ? dto.nombreJours() : duree);
            demande.setCodeMotif(dto.motifPermission());
            demande.setStatut(StatutDemande.BROUILLON);
            demande.setCircuitId(null);
            return demandeAbsenceRepository.save(demande);
        }

        if (dto.type() == TypeAbsence.CONGE_MATERNITE) {
            DemandeCongeMaternite demande = new DemandeCongeMaternite();
            demande.setDemandeurIdentifiantExterne(demandeurId);
            demande.setUniteIdentifiantExterne(claimReaderService.lireClaimReseau().orElse(demandeurId));
            demande.setType(TypeAbsence.CONGE_MATERNITE);
            demande.setDateDebut(dto.dateDebut());
            demande.setDateFin(dto.dateDebut().plusWeeks(14));
            demande.setNombreJours(98);
            demande.setEstProlongation(false);
            demande.setStatut(StatutDemande.BROUILLON);
            demande.setCircuitId(null);
            return demandeAbsenceRepository.save(demande);
        }

        DemandeCongeAnnuel demande = new DemandeCongeAnnuel();
        demande.setDemandeurIdentifiantExterne(demandeurId);
        demande.setUniteIdentifiantExterne(claimReaderService.lireClaimReseau().orElse(demandeurId));
        demande.setType(TypeAbsence.CONGE_ANNUEL);
        demande.setDateDebut(dto.dateDebut());
        demande.setDateFin(dto.dateFin());
        demande.setNombreJours(calculerJoursOuvres(dto.dateDebut(), dto.dateFin()));
        demande.setNumeroFraction(dto.numeroFraction());
        demande.setEstPremiereFraction(dto.estPremiereFraction());
        demande.setBackupIdentifiantExterne(dto.backupIdentifiantExterne());
        demande.setStatut(StatutDemande.BROUILLON);
        demande.setCircuitId(null);
        return demandeAbsenceRepository.save(demande);
    }

    @Override
    @Transactional
    public DemandeAbsence soumettre(UUID demandeId) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        verifierOwnership(demande);

        if (doublonDetectionService.detecterDoublon(demande) && !demande.isDoublonConfirme()) {
            throw new DoublonDetecteException(
                    "Une demande similaire existe deja sur une periode proche");
        }

        ModeleCircuit circuit = circuitDeterminationService
                .determinerCircuitApplicable(demande)
                .orElseThrow(() -> new CircuitNonDetermineException(
                        "Aucune regle d'affectation ne correspond au grade du demandeur"));

        demande.setCircuitId(circuit.getId());
        demande.setCircuitNom(nomCircuitTechnique(circuit));
        List<EtapeModeleCircuit> etapes = etapeModeleCircuitRepository
                .findByModeleCircuitIdOrderByOrdreAsc(circuit.getId());
        for (EtapeModeleCircuit etape : etapes) {
            EtapeDemandeSnapshot snap = new EtapeDemandeSnapshot();
            snap.setDemandeId(demande.getId());
            snap.setOrdre(etape.getOrdre());
            snap.setLibelle(etape.getLibelle());
            snap.setVerrouille(etape.isEstVerrouillable());
            etapeDemandeSnapshotRepository.save(snap);
        }
        demande.setPositionEtapeCourante(0);
        // Passage BROUILLON → SOUMISE via la machine à états
        stateMachine.sendEvent(demande, AbsenceEvent.SOUMETTRE);
        // Puis SOUMISE → EN_VALIDATION_ETAPE directement
        demande.setStatut(StatutDemande.EN_VALIDATION_ETAPE);
        return demandeAbsenceRepository.save(demande);
    }

    @Override
    public PreviewDemandeResponse previsualiser(UUID demandeId) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        verifierOwnership(demande);
        ModeleCircuit circuit = circuitDeterminationService
                .determinerCircuitApplicable(demande)
                .orElse(null);
        boolean doublon = doublonDetectionService.detecterDoublon(demande);
        return new PreviewDemandeResponse(demande, circuit, doublon, List.of());
    }

    @Override
    @Transactional
    public DemandeAbsence enregistrerDecisionEtape(UUID demandeId,
                                                    Decision decision, String motif) {
        String validateurId = claimReaderService.identifiantUtilisateurCourant();

        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));

        if (decision == Decision.REJETER && (motif == null || motif.isBlank())) {
            throw new MotifRequisException("Un motif est requis pour tout rejet");
        }

        EtapeDemandeSnapshot etapeCourante = etapeDemandeSnapshotRepository
                .findByDemandeIdAndPosition(demandeId, demande.getPositionEtapeCourante())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Etape courante introuvable pour la demande : " + demandeId));

        verifierRoleHabilite(etapeCourante, demande, validateurId);

        Validation validation = new Validation();
        validation.setDemandeId(demandeId);
        validation.setEtapeSnapshotId(etapeCourante.getId());
        validation.setValidateurIdentifiantExterne(validateurId);
        validation.setDecision(decision == Decision.REJETER
                ? Validation.DecisionValidation.REJETEE
                : Validation.DecisionValidation.APPROUVEE);
        validation.setCommentaire(motif);
        validation.setDateDecision(LocalDateTime.now());
        validationRepository.save(validation);

        stateMachine.sendEvent(demande,
                decision == Decision.REJETER ? AbsenceEvent.REJETER : AbsenceEvent.VALIDER);
        return demandeAbsenceRepository.save(demande);
    }

    // ── Habilitation générique (switch sur MecanismeResolution) ────────────────

    private void verifierRoleHabilite(EtapeDemandeSnapshot etape,
                                      DemandeAbsence demande,
                                      String validateurId) {
        MecanismeResolution mecanisme = etape.getMecanismeResolution();

        // Fallback : si mécanisme non renseigné (données anciennes), vérification Back-up par unité
        if (mecanisme == null) {
            verifierMemeUnite(etape, demande, validateurId);
            return;
        }

        switch (mecanisme) {
            case BACKUP -> {
                if (!validateurId.equals(demande.getBackupIdentifiantExterne())) {
                    throw new ValidateurNonAutoriseException(
                            "Le validateur n'est pas le backup désigné de la demande");
                }
            }
            case HIERARCHIQUE -> {
                int profondeur = etape.getPosition() != null ? etape.getPosition() : 1;
                boolean ok = hierarchicalChainResolver.verifierLienHierarchique(
                        validateurId, demande.getDemandeurIdentifiantExterne(), profondeur);
                if (!ok) throw new ValidateurNonAutoriseException(
                        "L'appelant n'est pas le N+" + profondeur + " du demandeur");
            }
            case ROLE_FIXE_SCOPE_RESEAU -> {
                Optional<String> reseauDemandeur = resoudreReseauDemandeur(demande);
                if (reseauDemandeur.isEmpty()) {
                    throw new ReseauNonRenseigneException(
                            "Impossible de resoudre le Directeur Reseau : le claim CLAIM_RESEAU du demandeur est absent");
                }
                Optional<String> reseauValidateur = validateurId.equals(
                        claimReaderService.identifiantUtilisateurCourant())
                        ? claimReaderService.lireClaimReseau()
                        : hierarchicalChainResolver.resoudreReseau(validateurId);
                if (reseauValidateur.isEmpty()
                        || !reseauValidateur.get().equals(reseauDemandeur.get())) {
                    throw new ValidateurNonAutoriseException(
                            "Le Directeur Reseau n'appartient pas au meme Reseau");
                }
            }
            case ROLE_FIXE_GLOBAL, DG_CONDITIONNEL -> {
                // Aucune restriction de périmètre — tout validateur authentifié est autorisé
            }
        }
    }

    /**
     * Résout le réseau du demandeur.
     * Si le demandeur est l'appelant courant, lit directement son claim JWT.
     * Sinon, interroge l'API admin Keycloak (même pattern que resoudreManagerDirect).
     */
    private Optional<String> resoudreReseauDemandeur(DemandeAbsence demande) {
        String demandeurId = demande.getDemandeurIdentifiantExterne();
        if (demandeurId.equals(claimReaderService.identifiantUtilisateurCourant())) {
            return claimReaderService.lireClaimReseau();
        }
        return hierarchicalChainResolver.resoudreReseau(demandeurId);
    }

    /** Vérification unité identique (Back-up, position 0). */
    private void verifierMemeUnite(EtapeDemandeSnapshot etape,
                                   DemandeAbsence demande,
                                   String validateurId) {
        String uniteValidateur = etape.getValidateurIdentifiantExterne();
        String uniteDemandeur  = demande.getUniteIdentifiantExterne();
        boolean memeUnite = uniteDemandeur != null && uniteDemandeur.equals(uniteValidateur);
        if (!memeUnite) {
            throw new ValidateurNonAutoriseException(
                    "VALIDATEUR_NON_AUTORISE : le validateur " + validateurId
                    + " n'appartient pas a l'unite du demandeur");
        }
    }

    @Override
    @Transactional
    public AbsenceResponse creer(AbsenceRequest request) {
        DemandeAbsence demande = new DemandeAbsence();
        demande.setDemandeurIdentifiantExterne(request.getDemandeurIdentifiantExterne());
        demande.setUniteIdentifiantExterne(request.getUniteIdentifiantExterne());
        demande.setDateDebut(request.getDateDebut());
        demande.setDateFin(request.getDateFin());
        demande.setNombreJours(request.getNombreJours());
        demande.setStatut(StatutDemande.BROUILLON);
        return toResponse(repository.save(demande));
    }

    @Override
    public AbsenceResponse findById(UUID id) {
        DemandeAbsence demande = getOrThrow(id);
        String userId = claimReaderService.identifiantUtilisateurCourant();
        if (!userId.equals(demande.getDemandeurIdentifiantExterne())
                && !userId.equals(demande.getBackupIdentifiantExterne())
                && !claimReaderService.estRolePrivilegie()) {
            throw new AccessDeniedException("Accès refusé à la demande " + id);
        }
        return toResponse(demande);
    }

    @Override
    public List<AbsenceResponse> findAll() {
        return toResponseBatch(repository.findAll());
    }

    @Override
    public List<AbsenceResponse> findByStatut(StatutDemande statut) {
        return toResponseBatch(repository.findByStatut(statut));
    }

    @Override
    public List<AbsenceResponse> findByDemandeurAndStatut(String demandeurIdentifiantExterne,
                                                           StatutDemande statut) {
        return toResponseBatch(repository.findByDemandeurIdentifiantExterneAndStatut(
                demandeurIdentifiantExterne, statut));
    }

    @Override
    public List<AbsenceResponse> findByBackup(String backupIdentifiantExterne) {
        return toResponseBatch(repository.findByBackupIdentifiantExterne(backupIdentifiantExterne));
    }

    @Override
    public SoldeCongeResponse findSoldeByDemandeur(String demandeurIdentifiantExterne) {
        int exercice = java.time.LocalDate.now().getYear();
        return soldeCongeRepository
                .findByEmployeIdentifiantExterneAndExercice(demandeurIdentifiantExterne, exercice)
                .map(s -> new SoldeCongeResponse(
                        s.getEmployeIdentifiantExterne(),
                        s.getExercice(),
                        s.getJoursAcquis(),
                        s.getJoursPris(),
                        s.getJoursRestants()))
                .orElse(new SoldeCongeResponse(
                        demandeurIdentifiantExterne, exercice, 0, 0, 0));
    }

    @Override
    @Transactional
    public AbsenceResponse updateStatut(UUID id, StatutUpdateRequest request) {
        DemandeAbsence demande = getOrThrow(id);
        demande.setStatut(request.getStatut());
        return toResponse(repository.save(demande));
    }

    @Override
    @Transactional
    public JustificatifDocument deposerJustificatif(UUID demandeId, String typePiece,
                                                     org.springframework.web.multipart.MultipartFile fichier) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        verifierOwnership(demande);
        String urlFichier = minioStorageService.uploader(fichier, typePiece, demandeId);
        JustificatifDocument doc = new JustificatifDocument();
        doc.setDemandeId(demandeId);
        doc.setTypePiece(typePiece);
        doc.setUrlFichier(urlFichier);
        doc.setDeposeLe(Instant.now());
        return justificatifDocumentRepository.save(doc);
    }

    @Override
    @Transactional
    public DemandeAbsence enregistrerDecisionDRH(UUID demandeId, Decision decision, String motif,
                                                  Integer nombreJoursAjuste) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));

        if (demande.getStatut() != StatutDemande.EN_VALIDATION_DRH) {
            throw new TransitionIllegaleException(
                    "La demande doit être au statut EN_VALIDATION_DRH — statut actuel : "
                            + demande.getStatut());
        }

        if (decision == Decision.VALIDER
                && TYPES_AVEC_JUSTIFICATIF.contains(demande.getType())
                && !justificatifDocumentRepository.existsByDemandeId(demandeId)) {
            throw new JustificatifRequisException("Justificatif manquant avant validation DRH");
        }

        if (decision == Decision.REJETER && (motif == null || motif.isBlank())) {
            throw new MotifRequisException("Un motif est requis pour tout rejet");
        }

        if (demande instanceof DemandePermission perm
                && "AUTRE_MOTIF".equals(perm.getCodeMotif())
                && nombreJoursAjuste != null) {
            demande.setNombreJours(nombreJoursAjuste);
        }

        stateMachine.sendEvent(demande,
                decision == Decision.REJETER ? AbsenceEvent.REJETER : AbsenceEvent.VALIDER);
        demandeAbsenceRepository.save(demande);

        if (decision == Decision.VALIDER) {
            // Débiter le solde pour les types concernés
            Set<TypeAbsence> typesAvecSolde = Set.of(TypeAbsence.CONGE_ANNUEL, TypeAbsence.PERMISSION);
            if (typesAvecSolde.contains(demande.getType()) && demande.getNombreJours() != null) {
                soldeCongeService.debiter(
                        demande.getDemandeurIdentifiantExterne(),
                        demande.getType(),
                        demande.getNombreJours());
            }
            documentMiseEnCongeService.genererDocumentMiseEnConge(demandeId);
        }
        return demande;
    }

    @Override
    @Transactional
    public DemandeAbsence creerProlongation(UUID demandeInitialeId,
                                            CreationProlongationRequest dto) {
        DemandeAbsence initiale = demandeAbsenceRepository.findById(demandeInitialeId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Demande introuvable : " + demandeInitialeId));
        verifierOwnership(initiale);

        if (initiale.getType() != TypeAbsence.CONGE_MATERNITE
                || initiale.getStatut() != StatutDemande.VALIDEE) {
            throw new ProlongationNonAutoriseeException(
                    "La demande initiale doit etre de type CONGE_MATERNITE et au statut VALIDEE");
        }

        DemandeCongeMaternite prolongation = new DemandeCongeMaternite();
        prolongation.setDemandeurIdentifiantExterne(initiale.getDemandeurIdentifiantExterne());
        prolongation.setUniteIdentifiantExterne(initiale.getUniteIdentifiantExterne());
        prolongation.setType(TypeAbsence.CONGE_MATERNITE);
        prolongation.setEstProlongation(true);
        prolongation.setDemandeInitialeId(demandeInitialeId);
        prolongation.setDateDebut(initiale.getDateFin().plusDays(1));
        prolongation.setDateFin(prolongation.getDateDebut().plusWeeks(6));
        prolongation.setNombreJours(42);
        prolongation.setCircuitId(initiale.getCircuitId());
        prolongation.setStatut(StatutDemande.BROUILLON);
        return demandeAbsenceRepository.save(prolongation);
    }

    @Override
    @Transactional
    public DemandeAbsence modifier(UUID demandeId, ModificationDemandeRequest dto) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        verifierOwnership(demande);
        if (!Set.of(StatutDemande.BROUILLON, StatutDemande.REJETEE).contains(demande.getStatut())) {
            throw new ModificationImpossibleException(
                    "Seules les demandes BROUILLON ou REJETEE sont modifiables");
        }
        demande.setDateDebut(dto.dateDebut());
        demande.setDateFin(dto.dateFin());
        demande.setNombreJours(dto.nombreJours());
        return demandeAbsenceRepository.save(demande);
    }

    @Override
    @Transactional
    public void supprimer(UUID id) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + id));
        verifierOwnership(demande);
        if (!Set.of(StatutDemande.BROUILLON, StatutDemande.REJETEE_PAR_LE_SYSTEME,
                StatutDemande.REJETEE).contains(demande.getStatut())) {
            throw new SuppressionImpossibleException(
                    "Seules les demandes BROUILLON, REJETEE_PAR_LE_SYSTEME ou REJETEE sont supprimables");
        }
        demandeAbsenceRepository.delete(demande);
    }

    @Override
    @Transactional
    public DemandeAbsence retourAnticipe(UUID demandeId, LocalDate dateRetourEffective) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        verifierOwnership(demande);

        Set<TypeAbsence> typesAvecSolde = Set.of(TypeAbsence.CONGE_ANNUEL, TypeAbsence.PERMISSION);
        if (typesAvecSolde.contains(demande.getType())) {
            long joursNonConsommes = ChronoUnit.DAYS.between(
                    dateRetourEffective, demande.getDateFin());
            if (joursNonConsommes > 0) {
                soldeCongeService.recrediter(
                        demande.getDemandeurIdentifiantExterne(),
                        demande.getType(),
                        (int) joursNonConsommes);
            }
        }

        systemeHabilitations.revoquerHabilitations(demande.getBackupIdentifiantExterne());

        demande.setStatut(StatutDemande.CLOTUREE);
        return demandeAbsenceRepository.save(demande);
    }

    private void verifierOwnership(DemandeAbsence demande) {
        String userId = claimReaderService.identifiantUtilisateurCourant();
        if (!userId.equals(demande.getDemandeurIdentifiantExterne())) {
            throw new AccessDeniedException("Accès refusé à la demande " + demande.getId());
        }
    }

    private DemandeAbsence getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + id));
    }

    private int calculerJoursOuvres(LocalDate dateDebut, LocalDate dateFin) {
        int joursOuvres = 0;
        LocalDate date = dateDebut;
        while (!date.isAfter(dateFin)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                joursOuvres++;
            }
            date = date.plusDays(1);
        }
        return joursOuvres;
    }

    private List<AbsenceResponse> toResponseBatch(List<DemandeAbsence> demandes) {
        if (demandes.isEmpty()) return List.of();
        List<UUID> ids = demandes.stream().map(DemandeAbsence::getId).toList();
        Map<UUID, List<JustificatifDocument>> byDemande = justificatifDocumentRepository
                .findAllByDemandeIdIn(ids).stream()
                .collect(Collectors.groupingBy(JustificatifDocument::getDemandeId));
        return demandes.stream()
                .map(d -> buildResponse(d, byDemande.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    private AbsenceResponse toResponse(DemandeAbsence d) {
        return buildResponse(d, justificatifDocumentRepository.findAllByDemandeId(d.getId()));
    }

    private AbsenceResponse buildResponse(DemandeAbsence d, List<JustificatifDocument> justificatifs) {
        AbsenceResponse.AbsenceResponseBuilder builder = AbsenceResponse.builder()
                .id(d.getId())
                .demandeurIdentifiantExterne(d.getDemandeurIdentifiantExterne())
                .uniteIdentifiantExterne(d.getUniteIdentifiantExterne())
                .statut(d.getStatut())
                .dateDebut(d.getDateDebut())
                .dateFin(d.getDateFin())
                .nombreJours(d.getNombreJours())
                .positionEtapeCourante(d.getPositionEtapeCourante())
                .motifRejetSysteme(d.getMotifRejetSysteme())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .justificatifs(justificatifs);

        if (d instanceof DemandeCongeAnnuel congeAnnuel) {
            builder.numeroFraction(congeAnnuel.getNumeroFraction())
                   .estPremiereFraction(congeAnnuel.getEstPremiereFraction());
        }

        builder.backupIdentifiantExterne(d.getBackupIdentifiantExterne());

        return builder.build();
    }

    private String nomCircuitTechnique(ModeleCircuit circuit) {
        String nom = circuit.getNom();
        return nom != null && nom.toUpperCase().contains("AGENT") ? "AGENT" : nom;
    }
}
