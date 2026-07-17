-- V17 — Journal d'audit du forçage de statut par ADMIN_RH (CDCT EX-9 / décision E, §4.1.7-E).
--
-- L'override PATCH /{id}/statut contourne la machine à états ET tous les effets de bord
-- (débit du solde, génération du document). Le CDCT impose de le conserver mais de l'encadrer :
-- motif obligatoire, cible VALIDEE interdite, et journalisation « qui / quand / ancien→nouveau
-- / motif ». Cette table porte le dernier point.
--
-- Pas de FK vers demande_absence — contrairement à `validation` (V1) qui est en ON DELETE
-- CASCADE. Un journal d'audit doit survivre à la disparition de son objet : supprimer une
-- demande ne doit pas effacer la preuve qu'un ADMIN_RH a forcé son statut. C'est précisément
-- la trace qu'un auditeur vient chercher. Le lien reste exploitable par demande_id.

CREATE TABLE audit_override_statut (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    demande_id                 UUID         NOT NULL,
    auteur_identifiant_externe VARCHAR(100) NOT NULL,
    statut_ancien              VARCHAR(40)  NOT NULL,
    statut_nouveau             VARCHAR(40)  NOT NULL,
    motif                      VARCHAR(500) NOT NULL,
    date_action                TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- NOT NULL ne suffit pas : une chaîne vide ou blanche viderait l'exigence de motif de
    -- son sens. Doublon assumé du @NotBlank applicatif — la base est la dernière ligne.
    CONSTRAINT chk_audit_override_motif_non_vide CHECK (BTRIM(motif) <> ''),
    CONSTRAINT chk_audit_override_cible CHECK (statut_nouveau <> 'VALIDEE')
);

CREATE INDEX idx_audit_override_demande ON audit_override_statut (demande_id);
