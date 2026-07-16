-- V16 — Aligne fn_verifier_unicite_decision_dg sur l'unicité PAR ÉTAPE.
--
-- Contexte : V1 définit l'unicité PAR DEMANDE — toute 2e décision est refusée dès qu'une
-- AUTRE étape a déjà décidé (clause `v.etape_snapshot_id != NEW.etape_snapshot_id`). Cette
-- règle est incompatible avec les circuits multi-étapes : la validation de l'étape 2
-- (Manager) échoue en UNICITE_DECISION_DG puisque l'étape 1 (Back-up) a déjà approuvé.
--
-- La fonction a été corrigée directement en base (CREATE OR REPLACE manuel), sans migration :
-- les environnements existants tournent sur une définition PAR ÉTAPE absente du code source.
-- Toute recréation de base (`docker compose down -v`) restaure donc la version V1 et casse
-- le circuit à la 2e étape. Cette migration matérialise le correctif appliqué à la main.
--
-- Corps identique à la définition en place : aucun changement de comportement sur les bases
-- existantes (remplacement à l'identique) ; sur une base neuve, corrige V1 après son passage.
--
-- Le trigger trg_unicite_decision_dg (V1) référence la fonction par son nom : remplacer le
-- corps suffit, le trigger n'est pas recréé.
--
-- NB nommage : le suffixe `_dg` et le libellé de V1 (« décision définitive ») datent d'une
-- règle DG par demande qui n'a plus cours. Le nom est conservé pour ne pas toucher au trigger ;
-- la règle effective est bien « une seule décision APPROUVEE/REJETEE par étape ».
--
-- NB portée : la garde ne couvre que les décisions APPROUVEE/REJETEE. Toute nouvelle valeur
-- de `decision` (cf. chk_decision) échapperait à l'unicité et devra être ajoutée ici.

CREATE OR REPLACE FUNCTION fn_verifier_unicite_decision_dg()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    nb INTEGER;
BEGIN
    SELECT COUNT(*) INTO nb
    FROM   validation v
    WHERE  v.demande_id        = NEW.demande_id
    AND    v.etape_snapshot_id = NEW.etape_snapshot_id
    AND    v.decision IN ('APPROUVEE', 'REJETEE');

    IF nb > 0 THEN
        RAISE EXCEPTION
            'UNICITE_DECISION_ETAPE: decision deja enregistree pour l etape % de la demande %',
            NEW.etape_snapshot_id, NEW.demande_id;
    END IF;
    RETURN NEW;
END;
$$;
