-- V15 — Réintroduit les étapes fixes « Instruction RH » (ANALYSTE_RH) et
-- « Validation DRH » (DRH) dans les circuits standard, en ROLE_FIXE_GLOBAL.
--
-- Contexte : V1 posait ces étapes, mais l'« Analyste RH » était en ROLE_FIXE_SCOPE_RESEAU,
-- donc INCLUSE dans les étapes intermédiaires → double validation. V12 les a donc toutes
-- supprimées (par libellé). Conséquence : les circuits standard n'affichent plus « Instruction
-- RH » ni « Validation DRH » dans la barre de progression, et les validateurs/signataires ne
-- sont plus enregistrés dans les snapshots.
--
-- On les remet ici en ROLE_FIXE_GLOBAL (comme les circuits mission de V14). Ce mécanisme est
-- EXCLU de findIntermediairesOrdonnees → la machine à états ne les traite pas comme des étapes
-- intermédiaires (aucune double validation), mais elles existent comme snapshots : visibles dans
-- la barre de progression, et le validateur (instruction / DRH) est correctement enregistré.
--
-- Data-driven (par nom + absence de DRH) car les circuits standard ont des UUID générés.
-- NB : n'affecte que les NOUVELLES demandes (snapshots créés à la soumission).

DO $$
DECLARE
    c         RECORD;
    max_ordre INT;
    eid       UUID;
BEGIN
    FOR c IN
        SELECT mc.id
        FROM modele_circuit mc
        WHERE (mc.nom LIKE 'Circuit Agent%'
            OR mc.nom LIKE 'Circuit Manager%'
            OR mc.nom LIKE 'Circuit Réseau%'
            OR mc.nom = 'Circuit Congé Maternité')
          AND NOT EXISTS (
              SELECT 1
              FROM etape_modele_circuit e
              JOIN regle_affectation r ON r.etape_modele_circuit_id = e.id
              WHERE e.modele_circuit_id = mc.id
                AND r.role_keycloak_cible = 'DRH'
          )
    LOOP
        SELECT COALESCE(MAX(ordre), 0) INTO max_ordre
        FROM etape_modele_circuit
        WHERE modele_circuit_id = c.id;

        -- Étape « Instruction RH » (Analyste RH)
        eid := gen_random_uuid();
        INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle)
        VALUES (eid, c.id, max_ordre + 1, 'Instruction RH');
        INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible)
        VALUES (eid, 'ROLE_FIXE_GLOBAL', 'ANALYSTE_RH');

        -- Étape « Validation DRH »
        eid := gen_random_uuid();
        INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle)
        VALUES (eid, c.id, max_ordre + 2, 'Validation DRH');
        INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible)
        VALUES (eid, 'ROLE_FIXE_GLOBAL', 'DRH');
    END LOOP;
END $$;
