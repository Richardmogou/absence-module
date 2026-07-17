-- ============================================================================
--  Seed de données — Tests de performance Module Absences
-- ============================================================================
--  Objectif : peupler la base à volume réaliste ET amorcer les files de
--  validation attendues par le scénario k6 (perf/k6/parcours-complet.js).
--
--  Produit :
--    1. Un solde de congé pour chaque agent synthétique (évite le no-op de débit)
--    2. Un gros volume de demandes en états terminaux (charge de LECTURE)
--    3. Des demandes EN_VALIDATION_ETAPE pré-assignées au MANAGER (worker validateur)
--    4. Des demandes EN_INSTRUCTION_ANALYSTE_RH        (worker analyste RH)
--    5. Des demandes EN_VALIDATION_DRH                 (worker DRH)
--
--  Toutes les données de test sont préfixées 'perf-' (demandeur/unité) → nettoyage
--  idempotent en tête de script. Le CASCADE de demande_absence purge snapshots,
--  validations et sous-tables.
--
--  ⚠️ AVANT DE LANCER : renseigner :manager_sub avec le `sub` Keycloak RÉEL du
--     compte "manager" utilisé par le worker `flowValidateurEtape` du script k6.
--     (analyste RH et DRH sont dépilés par RÔLE : aucun sub à renseigner pour eux.)
--
--  LANCEMENT :
--    docker exec -i absences-postgres \
--      psql -U absences_user -d absences -v ON_ERROR_STOP=1 -f - < perf/seed/seed_perf.sql
--    # ou en ajustant les volumes :
--    docker exec -i absences-postgres psql -U absences_user -d absences \
--      -v n_volume=100000 -v n_etape=1000 -f - < perf/seed/seed_perf.sql
-- ============================================================================

\timing on

-- ── Paramètres (surchargeables par -v nom=valeur) ───────────────────────────
\if :{?n_agents}      \else \set n_agents      500   \endif
\if :{?n_volume}      \else \set n_volume      50000 \endif
\if :{?n_etape}       \else \set n_etape       800   \endif
\if :{?n_instruction} \else \set n_instruction 500   \endif
\if :{?n_drh}         \else \set n_drh         500   \endif
\if :{?manager_sub}   \else \set manager_sub   'REMPLACER-PAR-LE-SUB-KEYCLOAK-DU-MANAGER' \endif

\echo '>>> Paramètres :'
\echo '    n_agents      =' :n_agents
\echo '    n_volume      =' :n_volume
\echo '    n_etape       =' :n_etape
\echo '    n_instruction =' :n_instruction
\echo '    n_drh         =' :n_drh
\echo '    manager_sub   =' :manager_sub

BEGIN;

-- ── 0. Nettoyage des données de test précédentes (idempotence) ──────────────
DELETE FROM demande_absence WHERE demandeur_identifiant_externe LIKE 'perf-%';
DELETE FROM solde_conge     WHERE employe_identifiant_externe   LIKE 'perf-%';

-- ── 1. Soldes de congé (1 ligne par agent synthétique, exercice courant) ────
INSERT INTO solde_conge
    (id, employe_identifiant_externe, exercice, jours_acquis, jours_pris, jours_restants, updated_at, version)
SELECT gen_random_uuid(),
       'perf-agent-' || lpad(g::text, 5, '0'),
       EXTRACT(YEAR FROM CURRENT_DATE)::int,
       30, 0, 30,                      -- 30 j acquis, 30 j restants → marge pour les débits
       NOW(), 0
FROM   generate_series(0, :n_agents - 1) g
ON CONFLICT (employe_identifiant_externe, exercice) DO NOTHING;

-- ── 2. VOLUME — demandes en états terminaux (charge de LECTURE) ─────────────
--     Répartition VALIDEE / REJETEE / CLOTUREE / BROUILLON pour exercer findAll,
--     les filtres par statut et la construction des réponses.
WITH nd AS (
    INSERT INTO demande_absence
        (id, type, statut, demandeur_identifiant_externe, unite_identifiant_externe,
         circuit_id, circuit_nom, position_etape_courante,
         date_debut, date_fin, nombre_jours, doublon_confirme, created_at, version)
    SELECT gen_random_uuid(),
           'CONGE_ANNUEL',
           (ARRAY['VALIDEE','REJETEE','CLOTUREE','BROUILLON','VALIDEE'])[1 + (g % 5)],
           'perf-agent-' || lpad((g % :n_agents)::text, 5, '0'),
           'perf-unite-' || (g % 10),
           (SELECT id FROM modele_circuit WHERE nom = 'Circuit Agent' LIMIT 1),
           'AGENT',
           NULL,
           (CURRENT_DATE - 200 + (g % 300))::date,
           (CURRENT_DATE - 200 + (g % 300) + 20)::date,
           15, false, NOW() - (g % 300) * INTERVAL '1 day', 0
    FROM   generate_series(1, :n_volume) g
    RETURNING id
)
INSERT INTO demande_conge_annuel (id, numero_fraction, est_premiere_fraction)
SELECT id, 1, false FROM nd;

-- ── 3. FILE VALIDATEUR — EN_VALIDATION_ETAPE, étape HIERARCHIQUE pré-assignée ─
--     validateur_identifiant_externe = :manager_sub → dépilable par le worker k6.
WITH nd AS (
    INSERT INTO demande_absence
        (id, type, statut, demandeur_identifiant_externe, unite_identifiant_externe,
         circuit_id, circuit_nom, position_etape_courante,
         date_debut, date_fin, nombre_jours, doublon_confirme, created_at, version)
    SELECT gen_random_uuid(),
           'CONGE_ANNUEL', 'EN_VALIDATION_ETAPE',
           'perf-agent-' || lpad((g % :n_agents)::text, 5, '0'),
           'perf-unite-' || (g % 10),
           (SELECT id FROM modele_circuit WHERE nom = 'Circuit Agent' LIMIT 1),
           'AGENT', 0,
           (CURRENT_DATE + 30 + (g % 200))::date,
           (CURRENT_DATE + 30 + (g % 200) + 20)::date,
           15, true, NOW(), 0
    FROM   generate_series(1, :n_etape) g
    RETURNING id
),
child AS (
    INSERT INTO demande_conge_annuel (id, numero_fraction, est_premiere_fraction)
    SELECT id, 1, false FROM nd
    RETURNING id
)
INSERT INTO etape_demande_snapshot
    (id, demande_id, ordre, libelle, validateur_identifiant_externe,
     statut, verrouille, mecanisme_resolution, position, role_habilite, date_affectation)
SELECT gen_random_uuid(), id, 1, 'Validation N+1',
       :'manager_sub', 'EN_COURS', false, 'HIERARCHIQUE', 0, NULL, NOW()
FROM   nd;

-- ── 4. FILE ANALYSTE RH — EN_INSTRUCTION_ANALYSTE_RH (dépilée par rôle) ──────
WITH nd AS (
    INSERT INTO demande_absence
        (id, type, statut, demandeur_identifiant_externe, unite_identifiant_externe,
         circuit_id, circuit_nom, position_etape_courante,
         date_debut, date_fin, nombre_jours, doublon_confirme, created_at, version)
    SELECT gen_random_uuid(),
           'CONGE_ANNUEL', 'EN_INSTRUCTION_ANALYSTE_RH',
           'perf-agent-' || lpad((g % :n_agents)::text, 5, '0'),
           'perf-unite-' || (g % 10),
           (SELECT id FROM modele_circuit WHERE nom = 'Circuit Agent' LIMIT 1),
           'AGENT', 1,
           (CURRENT_DATE + 30 + (g % 200))::date,
           (CURRENT_DATE + 30 + (g % 200) + 20)::date,
           15, true, NOW(), 0
    FROM   generate_series(1, :n_instruction) g
    RETURNING id
)
INSERT INTO demande_conge_annuel (id, numero_fraction, est_premiere_fraction)
SELECT id, 1, false FROM nd;

-- ── 5. FILE DRH — EN_VALIDATION_DRH (dépilée par rôle) ──────────────────────
WITH nd AS (
    INSERT INTO demande_absence
        (id, type, statut, demandeur_identifiant_externe, unite_identifiant_externe,
         circuit_id, circuit_nom, position_etape_courante,
         date_debut, date_fin, nombre_jours, doublon_confirme, created_at, version)
    SELECT gen_random_uuid(),
           'CONGE_ANNUEL', 'EN_VALIDATION_DRH',
           'perf-agent-' || lpad((g % :n_agents)::text, 5, '0'),
           'perf-unite-' || (g % 10),
           (SELECT id FROM modele_circuit WHERE nom = 'Circuit Agent' LIMIT 1),
           'AGENT', 2,
           (CURRENT_DATE + 30 + (g % 200))::date,
           (CURRENT_DATE + 30 + (g % 200) + 20)::date,
           15, true, NOW(), 0
    FROM   generate_series(1, :n_drh) g
    RETURNING id
)
INSERT INTO demande_conge_annuel (id, numero_fraction, est_premiere_fraction)
SELECT id, 1, false FROM nd;

COMMIT;

-- ── 6. Récapitulatif ────────────────────────────────────────────────────────
\echo ''
\echo '>>> Récapitulatif des données de test (perf-%) :'
SELECT statut, COUNT(*) AS nb
FROM   demande_absence
WHERE  demandeur_identifiant_externe LIKE 'perf-%'
GROUP  BY statut
ORDER  BY statut;

SELECT COUNT(*) AS soldes_perf
FROM   solde_conge
WHERE  employe_identifiant_externe LIKE 'perf-%';

SELECT COUNT(*) AS snapshots_file_validateur
FROM   etape_demande_snapshot s
JOIN   demande_absence d ON d.id = s.demande_id
WHERE  d.demandeur_identifiant_externe LIKE 'perf-%'
  AND  d.statut = 'EN_VALIDATION_ETAPE';
