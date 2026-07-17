package com.banque.absences.service;

import com.banque.absences.config.MinioProperties;
import com.banque.absences.repository.DemandeAbsenceRepository;
import com.banque.absences.repository.DocumentMiseEnCongeRepository;
import com.banque.absences.repository.EtapeDemandeSnapshotRepository;
import com.banque.absences.repository.ValidationRepository;
import com.banque.absences.service.pdf.PdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reprise de la génération asynchrone du document de mise en congé : la génération dépend
 * de Keycloak et de MinIO — une indisponibilité passagère ne doit pas perdre le document
 * au premier échec, et un échec définitif doit être borné (pas de boucle infinie).
 */
@ExtendWith(MockitoExtension.class)
class DocumentMiseEnCongeRetryTest {

    @Mock private DemandeAbsenceRepository demandeAbsenceRepository;
    @Mock private ValidationRepository validationRepository;
    @Mock private DocumentMiseEnCongeRepository documentMiseEnCongeRepository;
    @Mock private MinioStorageService minioStorageService;
    @Mock private MinioProperties minioProperties;
    @Mock private S3Client s3Client;
    @Mock private PdfService pdfService;
    @Mock private HierarchicalChainResolver hierarchicalChainResolver;
    @Mock private EtapeDemandeSnapshotRepository etapeDemandeSnapshotRepository;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private DocumentMiseEnCongeService service;

    private void executerCallbackReel() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("Échec persistant : 3 tentatives puis abandon signalé, sans exception propagée")
    void echecPersistant_troisTentativesPuisAbandon() {
        executerCallbackReel();
        when(demandeAbsenceRepository.findById(any()))
                .thenThrow(new RuntimeException("Keycloak indisponible"));

        service.genererDocumentMiseEnConge(UUID.randomUUID());

        verify(transactionTemplate, times(3)).executeWithoutResult(any());
    }

    @Test
    @DisplayName("Échec passager : la 2e tentative réussit, pas de 3e appel")
    void echecPassager_deuxiemeTentativeReussit() {
        final int[] appels = {0};
        doAnswer(invocation -> {
            if (++appels[0] == 1) {
                throw new RuntimeException("MinIO indisponible");
            }
            return null; // 2e tentative : la génération aboutit
        }).when(transactionTemplate).executeWithoutResult(any());

        service.genererDocumentMiseEnConge(UUID.randomUUID());

        verify(transactionTemplate, times(2)).executeWithoutResult(any());
    }
}
