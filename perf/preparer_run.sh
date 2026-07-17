#!/usr/bin/env bash
# Prépare la base pour un run k6 reproductible. À lancer SUR LE POSTE STACK
# (celui qui héberge docker) avant CHAQUE run — les fenêtres de dates du
# scénario étant déterministes par (VU, itération), un run sur données
# résiduelles produit des faux 409 CHEVAUCHEMENT.
#
# Usage : ./preparer_run.sh [manager_sub]
set -euo pipefail

MANAGER_SUB="${1:-c24d02a1-1ae2-4216-aa02-f0a91e3fe320}"   # sub Keycloak de perf-manager1
AGENT_SUBS="'a5de4562-bf39-46c7-83a9-7e11841ae865','752c45a2-6cd3-4144-80bc-9101623adda2'"
PSQL="docker exec -i absences-postgres psql -U absences_user -d absences -v ON_ERROR_STOP=1"

echo "1/3 — purge des demandes k6 (tables enfants d'abord : la cascade sur"
echo "      demande_absence est quadratique, cf. incident du 2026-07-17)"
$PSQL <<SQL
BEGIN;
CREATE TEMP TABLE cibles AS
    SELECT id FROM demande_absence
    WHERE demandeur_identifiant_externe IN (${AGENT_SUBS})
       OR (demandeur_identifiant_externe LIKE 'perf-%'
           AND statut IN ('EN_VALIDATION_ETAPE','EN_INSTRUCTION_ANALYSTE_RH','EN_VALIDATION_DRH'));
DELETE FROM validation             WHERE demande_id IN (SELECT id FROM cibles);
DELETE FROM etape_demande_snapshot WHERE demande_id IN (SELECT id FROM cibles);
DELETE FROM justificatif_document  WHERE demande_id IN (SELECT id FROM cibles);
DELETE FROM document_mise_en_conge WHERE demande_id IN (SELECT id FROM cibles);
DELETE FROM demande_mission_longue WHERE id IN (SELECT id FROM cibles);
DELETE FROM demande_mission        WHERE id IN (SELECT id FROM cibles);
DELETE FROM demande_conge_annuel   WHERE id IN (SELECT id FROM cibles);
DELETE FROM demande_permission     WHERE id IN (SELECT id FROM cibles);
DELETE FROM demande_conge_maladie  WHERE id IN (SELECT id FROM cibles);
DELETE FROM demande_conge_maternite WHERE id IN (SELECT id FROM cibles);
DELETE FROM demande_absence        WHERE id IN (SELECT id FROM cibles);
COMMIT;
SQL

echo "2/3 — seed volumétrique (50 000 demandes + files amorcées)"
$PSQL -v manager_sub="${MANAGER_SUB}" \
    < "$(dirname "$0")/seed/seed_perf.sql" > /dev/null

echo "3/3 — soldes dimensionnés pour la charge (le débit DRH tape l'exercice courant)"
$PSQL <<SQL
UPDATE solde_conge SET jours_acquis=100000000, jours_pris=0, jours_restants=100000000
WHERE employe_identifiant_externe LIKE 'perf-%'
   OR employe_identifiant_externe IN (${AGENT_SUBS});
SQL

echo "Base prête. Lancer k6 depuis le générateur (voir RUNBOOK_recette.md)."
