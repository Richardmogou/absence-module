-- V10__demande_mission_longue_objet_mission.sql
-- Ajout du champ objet_mission sur demande_mission_longue
-- pour persister l'objet / informations complémentaires saisi dans le formulaire front.

ALTER TABLE demande_mission_longue
    ADD COLUMN IF NOT EXISTS objet_mission VARCHAR(500);
