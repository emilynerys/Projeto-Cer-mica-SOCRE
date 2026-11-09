-- Status do Forno: the line-by-line map the kiln operator fills on paper, typed in by the Gestor de Produção.
-- Each save is a new picture of the kiln; earlier pictures stay as history.
CREATE TABLE status_forno (
    id uuid PRIMARY KEY,
    id_unidade uuid NOT NULL REFERENCES unidade(id),
    id_autor uuid NOT NULL REFERENCES usuario(id),
    data_hora timestamptz NOT NULL,
    observacao varchar(300),
    criado_em timestamptz NOT NULL
);
CREATE INDEX status_forno_unidade_data ON status_forno (id_unidade, data_hora DESC);

-- An empty line ("V" on the paper) has neither format nor status.
CREATE TABLE status_forno_linha (
    id_status uuid NOT NULL REFERENCES status_forno(id),
    linha integer NOT NULL CHECK (linha > 0),
    id_formato uuid REFERENCES formato(id),
    estado varchar(12) CHECK (estado IN ('ESQUENTE','QUEIMA','ASSADO','CRU','DANIFICADO')),
    quantidade integer NOT NULL CHECK (quantidade >= 0),
    PRIMARY KEY (id_status, linha),
    CHECK ((id_formato IS NULL) = (estado IS NULL))
);

-- A picture is never changed: a correction is a new picture.
GRANT SELECT, INSERT ON status_forno, status_forno_linha TO socre_app;
