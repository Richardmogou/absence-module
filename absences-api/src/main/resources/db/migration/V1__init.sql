-- ============================================================
-- V1__init.sql  —  DC-ABSENCES-v5.0  Migration initiale
-- ============================================================
-- RÈGLE : aucune table dont le nom contient "employe" n'est
-- créée ici. Les identifiants employés sont des VARCHAR
-- (clés Keycloak externes), jamais des FK vers une entité Employé.
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. RÉFÉRENTIEL : grille_permission
-- ────────────────────────────────────────────────────────────
CREATE TABLE grille_permission (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code_motif          VARCHAR(50)  NOT NULL UNIQUE,
    libelle             VARCHAR(200) NOT NULL,
    duree_jours         INTEGER      NOT NULL,
    justificatif_requis BOOLEAN      NOT NULL DEFAULT FALSE,
    actif               BOOLEAN      NOT NULL DEFAULT TRUE
);

-- 13 motifs barème permission (DC-ABSENCES-v5.0 §4.6)
INSERT INTO grille_permission (code_motif, libelle, duree_jours, justificatif_requis) VALUES
  ('MARIAGE_AGENT',        'Mariage de l''agent',                           5, TRUE),
  ('MARIAGE_ENFANT',       'Mariage d''un enfant',                          3, TRUE),
  ('DECES_CONJOINT',       'Décès du conjoint',                             5, TRUE),
  ('DECES_ENFANT',         'Décès d''un enfant',                            5, TRUE),
  ('DECES_PERE_MERE',      'Décès du père ou de la mère',                   5, TRUE),
  ('DECES_FRERE_SOEUR',    'Décès d''un frère ou d''une sœur',              3, TRUE),
  ('DECES_BEAU_PARENT',    'Décès d''un beau-parent',                       3, TRUE),
  ('NAISSANCE_ENFANT',     'Naissance d''un enfant',                        3, TRUE),
  ('HOSPITALISATION',      'Hospitalisation d''un proche (conjoint/enfant)',2, TRUE),
  ('EXAMEN_MEDICAL',       'Convocation examen médical obligatoire',        1, TRUE),
  ('EXAMEN_PROFESSIONNEL', 'Participation à un examen professionnel',       2, TRUE),
  ('DEMENAGEMENT',         'Déménagement du domicile principal',            2, FALSE),
  ('AUTRE_MOTIF',          'Autre motif exceptionnel',                      1, FALSE);

-- ────────────────────────────────────────────────────────────
-- 2. CIRCUITS DE VALIDATION
-- ────────────────────────────────────────────────────────────
CREATE TABLE modele_circuit (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nom                VARCHAR(100) NOT NULL,
    type_absence_cible VARCHAR(30)  NOT NULL,
    actif              BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_type_absence_cible CHECK (type_absence_cible IN (
        'CONGE_ANNUEL','CONGE_MALADIE','PERMISSION','MISSION_LONGUE','CONGE_MATERNITE'))
);

CREATE TABLE etape_modele_circuit (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    modele_circuit_id UUID         NOT NULL REFERENCES modele_circuit(id) ON DELETE CASCADE,
    ordre             INTEGER      NOT NULL,
    libelle           VARCHAR(100) NOT NULL,
    delai_jours       INTEGER,
    est_verrouillable BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_etape_ordre UNIQUE (modele_circuit_id, ordre)
);

CREATE TABLE regle_affectation (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    etape_modele_circuit_id UUID        NOT NULL REFERENCES etape_modele_circuit(id) ON DELETE CASCADE,
    mecanisme               VARCHAR(30) NOT NULL,
    profondeur_hierarchique INTEGER,
    role_keycloak_cible     VARCHAR(80),
    grade_declencheur       VARCHAR(50),
    priorite                INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT chk_mecanisme CHECK (mecanisme IN (
        'BACKUP','HIERARCHIQUE','ROLE_FIXE_SCOPE_RESEAU','ROLE_FIXE_GLOBAL','DG_CONDITIONNEL'))
);

-- 3 modèles de circuit par défaut (DC-ABSENCES-v5.0 §4.2)
DO $$
DECLARE
    cid_agent   UUID := gen_random_uuid();
    cid_manager UUID := gen_random_uuid();
    cid_reseau  UUID := gen_random_uuid();
    eid         UUID;
BEGIN
    -- ── Circuit AGENT : Congé Annuel ──────────────────────
    INSERT INTO modele_circuit (id, nom, type_absence_cible)
    VALUES (cid_agent, 'Circuit Agent', 'CONGE_ANNUEL');

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours)
    VALUES (gen_random_uuid(), cid_agent, 1, 'Back-up', 2)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, profondeur_hierarchique, grade_declencheur, priorite)
    VALUES (eid, 'HIERARCHIQUE', 1, 'AGENT', 1);

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours, est_verrouillable)
    VALUES (gen_random_uuid(), cid_agent, 2, 'Manager', 5, TRUE)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, profondeur_hierarchique, priorite)
    VALUES (eid, 'HIERARCHIQUE', 1, 2);

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours)
    VALUES (gen_random_uuid(), cid_agent, 3, 'Chef de processus', 3)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, role_keycloak_cible, priorite)
    VALUES (eid, 'ROLE_FIXE_SCOPE_RESEAU', 'CHEF_PROCESSUS', 1);

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours)
    VALUES (gen_random_uuid(), cid_agent, 4, 'Analyste RH', 3)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, role_keycloak_cible, priorite)
    VALUES (eid, 'ROLE_FIXE_SCOPE_RESEAU', 'ANALYSTE_RH', 1);

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours)
    VALUES (gen_random_uuid(), cid_agent, 5, 'DRH', 3)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, role_keycloak_cible, priorite)
    VALUES (eid, 'ROLE_FIXE_GLOBAL', 'DRH', 1);

    -- ── Circuit MANAGER : Congé Annuel ────────────────────
    INSERT INTO modele_circuit (id, nom, type_absence_cible)
    VALUES (cid_manager, 'Circuit Manager — Congé Annuel', 'CONGE_ANNUEL');

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours, est_verrouillable)
    VALUES (gen_random_uuid(), cid_manager, 1, 'Validation N+2 Hiérarchique', 5, TRUE)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, profondeur_hierarchique, grade_declencheur, priorite)
    VALUES (eid, 'HIERARCHIQUE', 2, 'MANAGER', 1);

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours)
    VALUES (gen_random_uuid(), cid_manager, 2, 'Validation DRH', 3)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, role_keycloak_cible, priorite)
    VALUES (eid, 'ROLE_FIXE_GLOBAL', 'DRH', 1);

    -- ── Circuit RESEAU : Congé Annuel ─────────────────────
    INSERT INTO modele_circuit (id, nom, type_absence_cible)
    VALUES (cid_reseau, 'Circuit Réseau — Congé Annuel', 'CONGE_ANNUEL');

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours)
    VALUES (gen_random_uuid(), cid_reseau, 1, 'Validation Responsable Réseau', 5)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur, priorite)
    VALUES (eid, 'ROLE_FIXE_SCOPE_RESEAU', 'RESPONSABLE_RESEAU', 'DA', 1);
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur, priorite)
    VALUES (eid, 'ROLE_FIXE_SCOPE_RESEAU', 'RESPONSABLE_RESEAU', 'CHEF_PROCESSUS', 1);

    INSERT INTO etape_modele_circuit
        (id, modele_circuit_id, ordre, libelle, delai_jours)
    VALUES (gen_random_uuid(), cid_reseau, 2, 'Validation DRH', 3)
    RETURNING id INTO eid;
    INSERT INTO regle_affectation
        (etape_modele_circuit_id, mecanisme, role_keycloak_cible, priorite)
    VALUES (eid, 'ROLE_FIXE_GLOBAL', 'DRH', 1);
END $$;

-- ────────────────────────────────────────────────────────────
-- 3. DEMANDES : table racine + sous-tables JOINED
-- ────────────────────────────────────────────────────────────
CREATE TABLE demande_absence (
    id                            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type                          VARCHAR(30)  NOT NULL,
    statut                        VARCHAR(40)  NOT NULL DEFAULT 'BROUILLON',
    demandeur_identifiant_externe VARCHAR(100) NOT NULL,
    circuit_id                    UUID         REFERENCES modele_circuit(id),
    position_etape_courante       INTEGER,
    date_debut                    DATE,
    date_fin                      DATE,
    nombre_jours                  INTEGER,
    unite_identifiant_externe     VARCHAR(100) NOT NULL,
    motif_rejet_systeme           VARCHAR(500),
    doublon_confirme              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMP,
    version                       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_statut_demande CHECK (statut IN (
        'BROUILLON','SOUMISE','REJETEE_PAR_LE_SYSTEME','EN_VALIDATION_ETAPE',
        'EN_INSTRUCTION_ANALYSTE_RH','EN_VALIDATION_DRH','VALIDEE',
        'REJETEE','DELEGUEE','CLOTUREE','ANNULEE')),
    CONSTRAINT chk_type_demande CHECK (type IN (
        'CONGE_ANNUEL','CONGE_MALADIE','PERMISSION','MISSION_LONGUE','CONGE_MATERNITE')),
    CONSTRAINT chk_dates CHECK (
        date_fin IS NULL OR date_debut IS NULL OR date_fin >= date_debut)
);

CREATE INDEX idx_demande_demandeur ON demande_absence (demandeur_identifiant_externe);
CREATE INDEX idx_demande_statut    ON demande_absence (statut);
CREATE INDEX idx_demande_circuit   ON demande_absence (circuit_id);

CREATE TABLE demande_conge_annuel (
    id                    UUID    PRIMARY KEY REFERENCES demande_absence(id) ON DELETE CASCADE,
    numero_fraction       INTEGER,
    est_premiere_fraction BOOLEAN
);

CREATE TABLE demande_conge_maladie (
    id UUID PRIMARY KEY REFERENCES demande_absence(id) ON DELETE CASCADE
);

CREATE TABLE demande_permission (
    id           UUID PRIMARY KEY REFERENCES demande_absence(id) ON DELETE CASCADE,
    evenement_id UUID REFERENCES grille_permission(id)
);

CREATE TABLE demande_mission_longue (
    id UUID PRIMARY KEY REFERENCES demande_absence(id) ON DELETE CASCADE
);

CREATE TABLE demande_conge_maternite (
    id                  UUID    PRIMARY KEY REFERENCES demande_absence(id) ON DELETE CASCADE,
    est_prolongation    BOOLEAN,
    demande_initiale_id UUID    REFERENCES demande_absence(id)
);

-- ────────────────────────────────────────────────────────────
-- 4. WORKFLOW : etape_demande_snapshot + validation
-- ────────────────────────────────────────────────────────────
CREATE TABLE etape_demande_snapshot (
    id                             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    demande_id                     UUID         NOT NULL REFERENCES demande_absence(id) ON DELETE CASCADE,
    ordre                          INTEGER      NOT NULL,
    libelle                        VARCHAR(100) NOT NULL,
    validateur_identifiant_externe VARCHAR(100),
    statut                         VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    verrouille                     BOOLEAN      NOT NULL DEFAULT FALSE,
    date_affectation               TIMESTAMP,
    date_limite                    TIMESTAMP,
    date_traitement                TIMESTAMP,
    CONSTRAINT chk_statut_etape CHECK (statut IN (
        'EN_ATTENTE','EN_COURS','VALIDEE','REJETEE','DELEGUEE','IGNOREE'))
);

CREATE INDEX idx_snapshot_demande ON etape_demande_snapshot (demande_id);

CREATE TABLE validation (
    id                             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    demande_id                     UUID         NOT NULL REFERENCES demande_absence(id) ON DELETE CASCADE,
    etape_snapshot_id              UUID         NOT NULL REFERENCES etape_demande_snapshot(id),
    validateur_identifiant_externe VARCHAR(100) NOT NULL,
    decision                       VARCHAR(20)  NOT NULL,
    commentaire                    VARCHAR(500),
    date_decision                  TIMESTAMP    NOT NULL DEFAULT NOW(),
    est_delegation                 BOOLEAN      NOT NULL DEFAULT FALSE,
    delegant_identifiant_externe   VARCHAR(100),
    CONSTRAINT chk_decision CHECK (decision IN ('APPROUVEE','REJETEE','DELEGUEE'))
);

CREATE INDEX idx_validation_demande ON validation (demande_id);

-- ────────────────────────────────────────────────────────────
-- 5. SOLDES : solde_conge
-- ────────────────────────────────────────────────────────────
CREATE TABLE solde_conge (
    id                          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_identifiant_externe VARCHAR(100) NOT NULL,
    exercice                    INTEGER NOT NULL,
    jours_acquis                INTEGER NOT NULL DEFAULT 0,
    jours_pris                  INTEGER NOT NULL DEFAULT 0,
    jours_restants              INTEGER NOT NULL DEFAULT 0,
    updated_at                  TIMESTAMP,
    version                     BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT uq_solde_employe_exercice UNIQUE (employe_identifiant_externe, exercice),
    CONSTRAINT chk_jours_positifs CHECK (
        jours_acquis >= 0 AND jours_pris >= 0 AND jours_restants >= 0)
);

-- ────────────────────────────────────────────────────────────
-- 6. TRIGGERS (DC-ABSENCES-v5.0 §4.2 et §4.5)
-- ────────────────────────────────────────────────────────────

-- Trigger 1 : interdit la modification d'une étape verrouillée
CREATE OR REPLACE FUNCTION fn_verifier_etapes_verrouillees()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.verrouille = TRUE AND NEW.verrouille = TRUE THEN
        RAISE EXCEPTION
            'ETAPE_VERROUILLEE: impossible de modifier l''étape % (demande %)',
            OLD.ordre, OLD.demande_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_etapes_verrouillees
BEFORE UPDATE ON etape_demande_snapshot
FOR EACH ROW EXECUTE FUNCTION fn_verifier_etapes_verrouillees();

-- Trigger 2 : interdit une deuxième décision définitive sur la même demande
CREATE OR REPLACE FUNCTION fn_verifier_unicite_decision_dg()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    nb INTEGER;
BEGIN
    SELECT COUNT(*) INTO nb
    FROM   validation v
    WHERE  v.demande_id = NEW.demande_id
    AND    v.decision   IN ('APPROUVEE', 'REJETEE')
    AND    v.etape_snapshot_id != NEW.etape_snapshot_id;

    IF nb > 0 THEN
        RAISE EXCEPTION
            'UNICITE_DECISION_DG: une décision définitive existe déjà pour la demande %',
            NEW.demande_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_unicite_decision_dg
BEFORE INSERT ON validation
FOR EACH ROW EXECUTE FUNCTION fn_verifier_unicite_decision_dg();
