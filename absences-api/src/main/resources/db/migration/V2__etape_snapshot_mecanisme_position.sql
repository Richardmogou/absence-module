-- V2 — Ajout des colonnes mecanisme_resolution et position
-- sur etape_demande_snapshot (US-AGT-003)

ALTER TABLE etape_demande_snapshot
    ADD COLUMN IF NOT EXISTS mecanisme_resolution VARCHAR(30),
    ADD COLUMN IF NOT EXISTS position             INTEGER;

COMMENT ON COLUMN etape_demande_snapshot.mecanisme_resolution IS
    'Mécanisme d''habilitation copié depuis regle_affectation à la soumission';
COMMENT ON COLUMN etape_demande_snapshot.position IS
    'Position de l''étape dans le circuit (= positionEtapeCourante de la demande)';
