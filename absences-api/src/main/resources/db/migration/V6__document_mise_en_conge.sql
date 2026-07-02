CREATE TABLE document_mise_en_conge (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    demande_id   UUID         NOT NULL,
    numero       VARCHAR(50)  NOT NULL,
    url_document VARCHAR(500) NOT NULL,
    genere_le    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_document_mise_en_conge PRIMARY KEY (id),
    CONSTRAINT uq_document_numero UNIQUE (numero)
);

CREATE INDEX idx_document_demande_id ON document_mise_en_conge (demande_id);
