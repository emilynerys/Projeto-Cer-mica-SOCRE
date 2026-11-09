-- Ajustes de 08/11/2026: login pelo nome da pessoa, troca de senha no primeiro acesso,
-- Status do Forno ligado às fornadas e estoque de tijolo assado contado no pátio.

-- The login is the person's name ("luiz"); the old e-mails keep only the part before "@" when that does not repeat.
ALTER TABLE usuario RENAME COLUMN email TO login;
UPDATE usuario u SET login = lower(split_part(u.login, '@', 1))
 WHERE position('@' in u.login) > 0
   AND NOT EXISTS (SELECT 1 FROM usuario o WHERE o.id <> u.id AND lower(split_part(o.login, '@', 1)) = lower(split_part(u.login, '@', 1)));
ALTER TABLE usuario ADD COLUMN trocar_senha boolean NOT NULL DEFAULT false;
-- The sessions keep the old e-mail; everyone enters again with the new login.
DELETE FROM spring_session;

-- Each kiln picture remembers which loads were in progress when it was saved.
CREATE TABLE status_forno_fornada (
    id_status uuid NOT NULL REFERENCES status_forno(id),
    id_fornada uuid NOT NULL REFERENCES fornada(id),
    PRIMARY KEY (id_status, id_fornada)
);
CREATE INDEX status_forno_fornada_fornada ON status_forno_fornada (id_fornada);

-- Burnt bricks counted in the yard, by format and quality; a new count never changes an old one.
CREATE TABLE estoque_patio (
    id uuid PRIMARY KEY,
    id_unidade uuid NOT NULL REFERENCES unidade(id),
    id_autor uuid NOT NULL REFERENCES usuario(id),
    data date NOT NULL,
    criado_em timestamptz NOT NULL
);
CREATE INDEX estoque_patio_unidade_data ON estoque_patio (id_unidade, data DESC, criado_em DESC);
CREATE TABLE estoque_patio_item (
    id_estoque uuid NOT NULL REFERENCES estoque_patio(id),
    id_formato uuid NOT NULL REFERENCES formato(id),
    primeira integer NOT NULL CHECK (primeira >= 0),
    segunda integer NOT NULL CHECK (segunda >= 0),
    PRIMARY KEY (id_estoque, id_formato)
);

GRANT SELECT, INSERT ON status_forno_fornada, estoque_patio, estoque_patio_item TO socre_app;
