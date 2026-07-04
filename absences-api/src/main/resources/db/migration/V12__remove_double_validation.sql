DELETE FROM regle_affectation
WHERE etape_modele_circuit_id IN (
    SELECT id FROM etape_modele_circuit WHERE libelle IN ('Analyste RH', 'DRH', 'Validation DRH')
);

DELETE FROM etape_modele_circuit
WHERE libelle IN ('Analyste RH', 'DRH', 'Validation DRH');
