package com.banque.absences.service;

import com.banque.absences.config.MinioProperties;
import com.banque.absences.domain.DemandeAbsence;
import com.banque.absences.domain.DocumentMiseEnConge;
import com.banque.absences.domain.Validation;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.DocumentMiseEnCongeRepository;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.repository.ValidationRepository;
import com.banque.absences.service.pdf.PdfService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentMiseEnCongeService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Africa/Abidjan"));

    private static final int TENTATIVES_MAX = 3;
    private static final long DELAI_INITIAL_MS = 2_000;

    private final DemandeAbsenceRepository      demandeAbsenceRepository;
    private final ValidationRepository          validationRepository;
    private final DocumentMiseEnCongeRepository documentMiseEnCongeRepository;
    private final MinioStorageService           minioStorageService;
    private final MinioProperties               minioProperties;
    private final S3Client                      s3Client;
    private final PdfService                    pdfService;
    private final HierarchicalChainResolver     hierarchicalChainResolver;
    private final EtapeDemandeSnapshotRepository etapeDemandeSnapshotRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    /**
     * Génération asynchrone avec reprise : la génération dépend de Keycloak (résolution des
     * signataires) et de MinIO (upload) — une indisponibilité passagère perdait le document
     * SANS AUCUN SIGNAL (échec avalé par l'exécuteur async). 3 tentatives espacées de
     * 2 s / 4 s, puis un ERROR marqué {@code ALERTE_DOCUMENT_NON_GENERE} : c'est le motif
     * sur lequel brancher la supervision — un document manquant est un incident RH, pas
     * une ligne de log de debug. Chaque tentative a sa propre transaction : un échec ne
     * laisse aucune écriture partielle.
     */
    @Async
    public void genererDocumentMiseEnConge(UUID demandeId) {
        RuntimeException derniereErreur = null;
        for (int tentative = 1; tentative <= TENTATIVES_MAX; tentative++) {
            try {
                transactionTemplate.executeWithoutResult(tx -> generer(demandeId));
                return;
            } catch (RuntimeException e) {
                derniereErreur = e;
                log.warn("Génération du document de mise en congé échouée pour la demande {} "
                        + "(tentative {}/{}) : {}", demandeId, tentative, TENTATIVES_MAX,
                        e.getMessage());
                if (tentative < TENTATIVES_MAX && !attendre(DELAI_INITIAL_MS << (tentative - 1))) {
                    break;
                }
            }
        }
        log.error("ALERTE_DOCUMENT_NON_GENERE demande={} : génération abandonnée après {} tentatives",
                demandeId, TENTATIVES_MAX, derniereErreur);
    }

    private boolean attendre(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void generer(UUID demandeId) {
        DemandeAbsence demande = demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        List<Validation> historique = validationRepository.findByDemandeId(demandeId);

        Map<String, Object> variables = new HashMap<>();
        
        // Logo en Base64
        try (java.io.InputStream is = new org.springframework.core.io.ClassPathResource("templates/img/logo_afb.png").getInputStream()) {
            String base64 = java.util.Base64.getEncoder().encodeToString(is.readAllBytes());
            variables.put("company_logo", "data:image/png;base64," + base64);
        } catch (Exception e) {
            log.warn("Impossible de charger le logo", e);
            variables.put("company_logo", "");
        }

        // Image de fond du header en Base64.
        try (java.io.InputStream is = new org.springframework.core.io.ClassPathResource("templates/img/background_header_light.png").getInputStream()) {
            String base64 = java.util.Base64.getEncoder().encodeToString(is.readAllBytes());
            variables.put("header_background", "data:image/png;base64," + base64);
        } catch (Exception e) {
            log.warn("Impossible de charger l'image de fond du header", e);
            variables.put("header_background", "");
        }

        // Bande Kente (rouge/gris/noir/clair) en Base64.
        try (java.io.InputStream is = new org.springframework.core.io.ClassPathResource("templates/img/kente-band.png").getInputStream()) {
            String base64 = java.util.Base64.getEncoder().encodeToString(is.readAllBytes());
            variables.put("kente_band", "data:image/png;base64," + base64);
        } catch (Exception e) {
            log.warn("Impossible de charger la la bande Kente", e);
            variables.put("kente_band", "");
        }

        // Champs de base
        variables.put("absence_type", demande.getType().toString());
        variables.put("date_debut", demande.getDateDebut() != null ? FMT.format(demande.getDateDebut().atStartOfDay(ZoneId.of("Africa/Abidjan")).toInstant()) : "");
        variables.put("date_fin", demande.getDateFin() != null ? FMT.format(demande.getDateFin().atStartOfDay(ZoneId.of("Africa/Abidjan")).toInstant()) : "");
        variables.put("nombre_jours", demande.getNombreJours());
        
        LocalDate dateReprise = demande.getDateFin() != null ? demande.getDateFin().plusDays(1) : null;
        variables.put("date_reprise", dateReprise != null ? FMT.format(dateReprise.atStartOfDay(ZoneId.of("Africa/Abidjan")).toInstant()) : "");
        variables.put("lieu_jouissance", "Non spécifié");

        // Champs avancés demandés par le template
        String demandeurId = demande.getDemandeurIdentifiantExterne();
        variables.put("issuing_department", "Direction des Ressources Humaines");
        variables.put("employee_full_name", hierarchicalChainResolver.resolveNomComplet(demandeurId).orElse(demandeurId));
        variables.put("employee_matricule", demandeurId);
        variables.put("employee_position", hierarchicalChainResolver.resoudreGradeParIdentifiant(demandeurId).orElse("Non renseigné"));
        variables.put("employee_department", hierarchicalChainResolver.resoudreReseau(demandeurId).orElse("Non renseigné"));
        
        String managerId = hierarchicalChainResolver.resoudreManagerDirect(demandeurId).orElse(null);
        if (managerId != null) {
            variables.put("direct_manager_name", hierarchicalChainResolver.resolveNomComplet(managerId).orElse(managerId));
        } else {
            variables.put("direct_manager_name", "Non renseigné");
        }

        variables.put("document_location", "Abidjan");
        variables.put("document_date", FMT.format(Instant.now()));
        
        // Trouver Analyste RH et DRH
        String drhName = "";
        String analysteName = "";
        
        List<com.banque.absences.domain.EtapeDemandeSnapshot> etapes = etapeDemandeSnapshotRepository.findByDemandeIdOrderByOrdreAsc(demandeId);
        
        // Analyste RH
        com.banque.absences.domain.EtapeDemandeSnapshot etapeAnalyste = etapes.stream()
                .filter(e -> "ANALYSTE_RH".equals(e.getRoleHabilite())).findFirst().orElse(null);
        if (etapeAnalyste != null && etapeAnalyste.getValidateurIdentifiantExterne() != null) {
            analysteName = hierarchicalChainResolver.resolveNomComplet(etapeAnalyste.getValidateurIdentifiantExterne()).orElse(etapeAnalyste.getValidateurIdentifiantExterne());
        }
        
        // DRH
        com.banque.absences.domain.EtapeDemandeSnapshot etapeDrh = etapes.stream()
                .filter(e -> "DRH".equals(e.getRoleHabilite())).findFirst().orElse(null);
        if (etapeDrh != null && etapeDrh.getValidateurIdentifiantExterne() != null) {
            drhName = hierarchicalChainResolver.resolveNomComplet(etapeDrh.getValidateurIdentifiantExterne()).orElse(etapeDrh.getValidateurIdentifiantExterne());
        }
                
        variables.put("hr_signatory_title", "Le Directeur des Ressources Humaines");
        variables.put("hr_signatory_name", drhName);
        variables.put("analyste_rh_name", analysteName);
        
        DateTimeFormatter tsFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Africa/Abidjan"));
        variables.put("generation_timestamp", tsFmt.format(Instant.now()));

        String templateName = "titre-conge";
        if (demande instanceof com.banque.absences.domain.DemandeMission m) {
            templateName = "ordre-mission";
            variables.put("destination", m.getDestination());
            variables.put("objet_mission", m.getObjetMission());
            variables.put("motif_mission", m.getMotifMission());
            variables.put("categorie", m.getCategorie());
        } else if (demande instanceof com.banque.absences.domain.DemandeMissionLongue m) {
            templateName = "ordre-mission";
            variables.put("destination", m.getDestination());
            variables.put("objet_mission", m.getObjetMission());
            variables.put("motif_mission", m.getMotifMission());
            variables.put("categorie", m.getCategorie());
        }

        byte[] pdfBytes = pdfService.generatePdf(templateName, variables);

        String numero    = genererNumeroUnique();
        String objectKey = "documents-mise-en-conge/" + demandeId + "/" + numero + ".pdf";

        // Upload vers MinIO
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(minioProperties.getBucket())
                .key(objectKey)
                .contentType("application/pdf")
                .contentLength((long) pdfBytes.length)
                .build();
        s3Client.putObject(req, RequestBody.fromBytes(pdfBytes));

        String urlDocument = minioProperties.getEndpoint()
                + "/" + minioProperties.getBucket()
                + "/" + objectKey;

        DocumentMiseEnConge doc = new DocumentMiseEnConge();
        doc.setDemandeId(demandeId);
        doc.setNumero(numero);
        doc.setUrlDocument(urlDocument);
        doc.setGenereLe(Instant.now());
        documentMiseEnCongeRepository.save(doc);

        log.info("Document mise en congé généré : {} → {}", numero, urlDocument);
    }

    private String genererNumeroUnique() {
        return "DMC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String construireContenuDocument(DemandeAbsence demande,
                                             List<Validation> historique) {
        StringBuilder sb = new StringBuilder();
        sb.append("====== DOCUMENT DE MISE EN CONGE ======\n");
        sb.append("Employé  : ").append(demande.getDemandeurIdentifiantExterne()).append("\n");
        sb.append("Type     : ").append(demande.getType()).append("\n");
        sb.append("Début    : ").append(demande.getDateDebut()).append("\n");
        sb.append("Fin      : ").append(demande.getDateFin()).append("\n");
        sb.append("Jours    : ").append(demande.getNombreJours()).append("\n");
        sb.append("\n--- Historique des validations ---\n");
        historique.forEach(v ->
                sb.append(" - ").append(v.getValidateurIdentifiantExterne())
                  .append(" : ").append(v.getDecision())
                  .append(" (").append(FMT.format(v.getDateDecision().atZone(
                          ZoneId.of("Africa/Abidjan")).toInstant())).append(")\n")
        );
        sb.append("======================================\n");
        return sb.toString();
    }
}
