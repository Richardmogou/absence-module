CREATE TABLE justificatif_document (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    demande_id          UUID         NOT NULL,
    type_piece          VARCHAR(100) NOT NULL,
    url_fichier         VARCHAR(500) NOT NULL,
    depose_le           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_justificatif_document PRIMARY KEY (id)
);

CREATE INDEX idx_justificatif_demande_id ON justificatif_document (demande_id);
