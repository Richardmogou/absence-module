CREATE TABLE demande_mission (
    id UUID PRIMARY KEY,
    objet_mission VARCHAR(500),
    motif_mission VARCHAR(1000),
    destination VARCHAR(200),
    categorie VARCHAR(100),
    CONSTRAINT fk_demande_mission_absence FOREIGN KEY (id) REFERENCES demande_absence(id) ON DELETE CASCADE
);

ALTER TABLE demande_mission_longue ADD COLUMN motif_mission VARCHAR(1000);
ALTER TABLE demande_mission_longue ADD COLUMN destination VARCHAR(200);
ALTER TABLE demande_mission_longue ADD COLUMN categorie VARCHAR(100);
