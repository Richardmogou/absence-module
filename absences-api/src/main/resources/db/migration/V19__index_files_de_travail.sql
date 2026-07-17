-- V19 — Index des files de travail (/a-valider), suite au test de performance du 2026-07-17.
--
-- Les files EN_INSTRUCTION_ANALYSTE_RH / EN_VALIDATION_DRH se lisent désormais bornées
-- et triées FIFO : (statut, created_at) sert le filtre ET l'ordre en un seul parcours.
-- Remplace idx_demande_statut, dont il est un sur-ensemble (préfixe statut).

CREATE INDEX idx_demande_statut_created ON demande_absence (statut, created_at);
DROP INDEX idx_demande_statut;

-- Branche « validateur pré-assigné » de la requête /a-valider. Partiel : la colonne
-- est NULL sur la majorité des snapshots (étapes non pré-assignées), inutile de les indexer.
CREATE INDEX idx_snapshot_validateur ON etape_demande_snapshot (validateur_identifiant_externe)
    WHERE validateur_identifiant_externe IS NOT NULL;
