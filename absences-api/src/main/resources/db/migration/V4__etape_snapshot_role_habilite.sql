ALTER TABLE etape_demande_snapshot
    ADD COLUMN IF NOT EXISTS role_habilite VARCHAR(80);

COMMENT ON COLUMN etape_demande_snapshot.role_habilite IS
    'Role habilite attendu pour les controles et audits de decision';
