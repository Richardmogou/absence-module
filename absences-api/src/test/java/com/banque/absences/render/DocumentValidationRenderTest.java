package com.banque.absences.render;

import com.banque.absences.domain.DemandeCongeAnnuel;
import com.banque.absences.domain.EtapeDemandeSnapshot;
import com.banque.absences.domain.MecanismeResolution;
import com.banque.absences.domain.StatutDemande;
import com.banque.absences.domain.TypeAbsence;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.service.DocumentMiseEnCongeService;
import com.banque.absences.service.HierarchicalChainResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rend le document RÉELLEMENT généré côté validation (genererDocumentMiseEnConge),
 * avec des snapshots Analyste RH / DRH réalistes et les noms résolus, en capturant
 * le PDF au moment de l'upload MinIO (S3Client mocké en profil test).
 */
@SpringBootTest
@ActiveProfiles("test")
class DocumentValidationRenderTest {

    private static final String OUT = "C:/Users/EUROPE~1/AppData/Local/Temp/claude/"
            + "C--Users-EUROPEOLINE-Desktop-INTRA-EHR-absence/"
            + "350fa3f8-09a0-4c36-8f92-66f3dbbc558f/scratchpad/document-validation.pdf";

    @Autowired DocumentMiseEnCongeService       service;
    @Autowired DemandeAbsenceRepository          demandeRepo;
    @Autowired EtapeDemandeSnapshotRepository    snapshotRepo;
    @Autowired S3Client                          s3Client; // mock @Primary (TestMinioConfig)

    @MockBean HierarchicalChainResolver resolver;

    @Test
    void genere_document_cote_validation() throws Exception {
        // Résolution des noms (Keycloak mocké)
        when(resolver.resolveNomComplet(any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.of(switch (id) {
                case "agent.mbaye"     -> "MBAYE Awa";
                case "manager.diop"    -> "DIOP Cadre";
                case "analyste.ndiaye" -> "NDIAYE Analyste";
                case "drh.sarr"        -> "SARR Directeur";
                default                -> id;
            });
        });
        when(resolver.resoudreGradeParIdentifiant(any())).thenReturn(Optional.of("Chargé de clientèle"));
        when(resolver.resoudreReseau(any())).thenReturn(Optional.of("CENTRE_EST"));
        when(resolver.resoudreManagerDirect(any())).thenReturn(Optional.of("manager.diop"));

        // Demande validée
        DemandeCongeAnnuel d = new DemandeCongeAnnuel();
        d.setDemandeurIdentifiantExterne("agent.mbaye");
        d.setUniteIdentifiantExterne("DSI");
        d.setType(TypeAbsence.CONGE_ANNUEL);
        d.setDateDebut(LocalDate.of(2026, 8, 3));
        d.setDateFin(LocalDate.of(2026, 8, 14));
        d.setNombreJours(12);
        d.setStatut(StatutDemande.VALIDEE);
        UUID id = demandeRepo.saveAndFlush(d).getId();

        // Snapshots Analyste RH + DRH avec validateurs (comme après instruction + validation DRH)
        snapshotRepo.saveAndFlush(snap(id, 4, "Instruction RH", "ANALYSTE_RH", "analyste.ndiaye"));
        snapshotRepo.saveAndFlush(snap(id, 5, "Validation DRH", "DRH", "drh.sarr"));

        // Génération réelle (déclenchée à la validation)
        service.genererDocumentMiseEnConge(id);

        // Capture du PDF au moment de l'upload MinIO
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, timeout(20000)).putObject(any(PutObjectRequest.class), body.capture());

        byte[] pdf = body.getValue().contentStreamProvider().newStream().readAllBytes();
        Path out = Path.of(OUT);
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        System.out.println("VALIDATION_PDF_WRITTEN=" + out + " size=" + pdf.length);
    }

    private EtapeDemandeSnapshot snap(UUID demandeId, int ordre, String libelle, String role, String validateur) {
        EtapeDemandeSnapshot s = new EtapeDemandeSnapshot();
        s.setDemandeId(demandeId);
        s.setOrdre(ordre);
        s.setPosition(ordre);
        s.setLibelle(libelle);
        s.setRoleHabilite(role);
        s.setMecanismeResolution(MecanismeResolution.ROLE_FIXE_GLOBAL);
        s.setValidateurIdentifiantExterne(validateur);
        s.setStatut(EtapeDemandeSnapshot.StatutEtape.VALIDEE);
        return s;
    }
}
