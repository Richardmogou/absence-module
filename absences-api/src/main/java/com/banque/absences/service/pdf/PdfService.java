package com.banque.absences.service.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
public class PdfService {

    @Autowired
    private TemplateEngine templateEngine;

    /**
     * Génère un fichier PDF à partir d'un template Thymeleaf et de variables.
     *
     * @param templateName Nom du template HTML (situé dans src/main/resources/templates/)
     * @param variables    Map contenant les variables à injecter dans le template
     * @return Le contenu du PDF sous forme de tableau d'octets
     */
    public byte[] generatePdf(String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            
            // 1. Rendu du template HTML avec Thymeleaf
            String html = templateEngine.process(templateName, context);

            // 2. Génération du PDF avec OpenHTMLToPDF
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(html, null);
                builder.toStream(outputStream);
                builder.run();
                
                byte[] pdfBytes = outputStream.toByteArray();
                if (pdfBytes.length == 0) {
                    throw new RuntimeException("Le PDF généré est vide.");
                }
                return pdfBytes;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF: " + e.getMessage(), e);
        }
    }
}
