-- US-ADM-001 : les circuits composés librement n'ont pas de type_absence_cible
-- et sont distingués des modèles nommés par est_modele_nomme.
ALTER TABLE modele_circuit
    ALTER COLUMN type_absence_cible DROP NOT NULL;

ALTER TABLE modele_circuit
    ADD COLUMN est_modele_nomme BOOLEAN NOT NULL DEFAULT FALSE;
