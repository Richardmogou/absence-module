-- V14__matrice_missions_approbations.sql
-- Implémentation de la Matrice d'approbation et d'autorisation pour les missions

-- 1. Mise à jour de la contrainte CHECK sur le type_absence_cible pour accepter les nouveaux types
ALTER TABLE modele_circuit DROP CONSTRAINT IF EXISTS chk_type_absence_cible;
ALTER TABLE modele_circuit ADD CONSTRAINT chk_type_absence_cible 
    CHECK (type_absence_cible IN ('CONGE_ANNUEL', 'CONGE_MALADIE', 'PERMISSION', 'MISSION', 'MISSION_LONGUE', 'MISSION_LOCALE', 'MISSION_INTERNATIONALE', 'CONGE_MATERNITE'));

-- =====================================================================================
-- CIRCUIT 1 : DIRECTEUR GÉNÉRAL (Mission Locale & Internationale)
-- Approbation : PCA | Autorisation : PCA
-- =====================================================================================
INSERT INTO modele_circuit (id, nom, type_absence_cible, actif) 
VALUES ('c1000000-0000-0000-0000-000000000001', 'Circuit DG - Mission', 'MISSION_INTERNATIONALE', true);

INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 1, 'Approbation PCA');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur)
VALUES ('e1000000-0000-0000-0000-000000000001', 'ROLE_FIXE_GLOBAL', 'PCA', 'Directeur Général');

-- =====================================================================================
-- CIRCUIT 2 : AUTRES PERSONNELS - MISSION LOCALE
-- Approbation : N+1 | Autorisation : Chef d'unité | RH | DRH
-- =====================================================================================
INSERT INTO modele_circuit (id, nom, type_absence_cible, actif) 
VALUES ('c2000000-0000-0000-0000-000000000001', 'Circuit Autres Personnels - Mission Locale', 'MISSION_LOCALE', true);

-- Étape 1 : Approbation N+1
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e2000000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-000000000001', 1, 'Approbation Supérieur hiérarchique');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, grade_declencheur)
VALUES ('e2000000-0000-0000-0000-000000000001', 'HIERARCHIQUE', 'Autres Personnels');

-- Étape 2 : Autorisation Chef d'unité (Chef de processus)
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e2000000-0000-0000-0000-000000000002', 'c2000000-0000-0000-0000-000000000001', 2, 'Autorisation Chef de Processus');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur)
VALUES ('e2000000-0000-0000-0000-000000000002', 'ROLE_FIXE_SCOPE_RESEAU', 'CHEF_PROCESSUS', 'Autres Personnels');

-- Étape 3 : Analyste RH
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e2000000-0000-0000-0000-000000000003', 'c2000000-0000-0000-0000-000000000001', 3, 'Instruction RH');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur)
VALUES ('e2000000-0000-0000-0000-000000000003', 'ROLE_FIXE_GLOBAL', 'ANALYSTE_RH', 'Autres Personnels');

-- Étape 4 : DRH
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e2000000-0000-0000-0000-000000000004', 'c2000000-0000-0000-0000-000000000001', 4, 'Validation DRH');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur)
VALUES ('e2000000-0000-0000-0000-000000000004', 'ROLE_FIXE_GLOBAL', 'DRH', 'Autres Personnels');

-- =====================================================================================
-- CIRCUIT 3 : AUTRES PERSONNELS - MISSION INTERNATIONALE
-- Approbation : N+1 | Autorisation : DG | RH | DRH
-- =====================================================================================
INSERT INTO modele_circuit (id, nom, type_absence_cible, actif) 
VALUES ('c3000000-0000-0000-0000-000000000001', 'Circuit Autres Personnels - Mission Inter', 'MISSION_INTERNATIONALE', true);

-- Étape 1 : Approbation N+1
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e3000000-0000-0000-0000-000000000001', 'c3000000-0000-0000-0000-000000000001', 1, 'Approbation Supérieur hiérarchique');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, grade_declencheur)
VALUES ('e3000000-0000-0000-0000-000000000001', 'HIERARCHIQUE', 'Autres Personnels');

-- Étape 2 : Autorisation DG
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e3000000-0000-0000-0000-000000000002', 'c3000000-0000-0000-0000-000000000001', 2, 'Autorisation Directeur Général');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur)
VALUES ('e3000000-0000-0000-0000-000000000002', 'ROLE_FIXE_GLOBAL', 'DG', 'Autres Personnels');

-- Étape 3 : Analyste RH
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e3000000-0000-0000-0000-000000000003', 'c3000000-0000-0000-0000-000000000001', 3, 'Instruction RH');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur)
VALUES ('e3000000-0000-0000-0000-000000000003', 'ROLE_FIXE_GLOBAL', 'ANALYSTE_RH', 'Autres Personnels');

-- Étape 4 : DRH
INSERT INTO etape_modele_circuit (id, modele_circuit_id, ordre, libelle) 
VALUES ('e3000000-0000-0000-0000-000000000004', 'c3000000-0000-0000-0000-000000000001', 4, 'Validation DRH');
INSERT INTO regle_affectation (etape_modele_circuit_id, mecanisme, role_keycloak_cible, grade_declencheur)
VALUES ('e3000000-0000-0000-0000-000000000004', 'ROLE_FIXE_GLOBAL', 'DRH', 'Autres Personnels');
