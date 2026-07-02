package com.banque.absences.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SystemeHabilitations {

    @Async
    public void revoquerHabilitations(String backupIdentifiantExterne) {
        if (backupIdentifiantExterne == null || backupIdentifiantExterne.isBlank()) return;
        // Appel vers le système d'habilitations externe (stub)
    }
}
