-- V18 — Journalise l'instruction de l'analyste RH dans `validation`.
--
-- Jusqu'ici, `instruire` écrivait le validateur et la date sur le SNAPSHOT d'étape, mais ne
-- créait aucune ligne dans `validation`. La décision de l'analyste RH n'existait donc dans
-- aucun journal durable — alors que c'est lui qui fixe la date de début d'un congé maternité,
-- et donc les 98 jours. Seul le circuit hiérarchique était tracé.
--
-- INSTRUITE, et non APPROUVEE : instruire n'est pas approuver. L'analyste RH renseigne un
-- dossier, il ne valide pas hiérarchiquement. Réutiliser APPROUVEE ferait dire au journal une
-- décision qui n'a pas eu lieu — précisément ce qu'un audit vient vérifier.
--
-- Pas de reprise de l'historique : les instructions passées ne sont pas rétro-remplies. On
-- pourrait les déduire de `etape_demande_snapshot.date_traitement`, mais ce serait fabriquer
-- des enregistrements d'audit. Un trou assumé et daté vaut mieux qu'un faux horodatage : le
-- journal des instructions démarre à cette migration.

ALTER TABLE validation DROP CONSTRAINT chk_decision;
ALTER TABLE validation ADD  CONSTRAINT chk_decision
    CHECK (decision IN ('APPROUVEE', 'REJETEE', 'DELEGUEE', 'INSTRUITE'));

-- Le trigger d'unicité (V1, réaligné par V16) n'énumère que les décisions qu'il garde. Sans
-- cet ajout, INSTRUITE échapperait au contrôle : une même étape pourrait porter plusieurs
-- instructions.
CREATE OR REPLACE FUNCTION fn_verifier_unicite_decision_dg()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    nb INTEGER;
BEGIN
    SELECT COUNT(*) INTO nb
    FROM   validation v
    WHERE  v.demande_id        = NEW.demande_id
    AND    v.etape_snapshot_id = NEW.etape_snapshot_id
    AND    v.decision IN ('APPROUVEE', 'REJETEE', 'INSTRUITE');

    IF nb > 0 THEN
        RAISE EXCEPTION
            'UNICITE_DECISION_ETAPE: decision deja enregistree pour l etape % de la demande %',
            NEW.etape_snapshot_id, NEW.demande_id;
    END IF;
    RETURN NEW;
END;
$$;
