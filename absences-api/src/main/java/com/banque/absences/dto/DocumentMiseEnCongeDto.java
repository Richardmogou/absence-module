package com.banque.absences.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class DocumentMiseEnCongeDto {
    private String numero;
    private String urlDocument;
    private Instant genereLe;
}
