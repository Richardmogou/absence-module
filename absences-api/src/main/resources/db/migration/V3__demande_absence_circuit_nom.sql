ALTER TABLE demande_absence
    ADD COLUMN IF NOT EXISTS circuit_nom VARCHAR(100);

COMMENT ON COLUMN demande_absence.circuit_nom IS
    'Nom technique du circuit retenu pour les règles conditionnelles de workflow';
