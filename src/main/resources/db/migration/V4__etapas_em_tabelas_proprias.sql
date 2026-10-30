-- Decisão de 30/10/2026: cada etapa tem tabela própria (maromba, enforna, desenforna)
-- e cada registro é um formato em um dia. Também cria formato, motivo, fornada e
-- inventário inicial, e passa as chaves estrangeiras para o padrão id_<tabela>.

ALTER TABLE unidade ADD COLUMN linhas_forno integer NOT NULL DEFAULT 46 CHECK (linhas_forno > 0);
ALTER TABLE unidade ALTER COLUMN linhas_forno DROP DEFAULT;
ALTER TABLE usuario RENAME COLUMN unidade_id TO id_unidade;

CREATE TABLE formato (
    id uuid PRIMARY KEY,
    nome varchar(60) NOT NULL,
    tijolos_por_linha integer NOT NULL CHECK (tijolos_por_linha > 0),
    ativo boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX formato_nome_unico ON formato (lower(nome));
-- Fixed ids let this migration move the old 6F/8F columns into rows.
INSERT INTO formato (id, nome, tijolos_por_linha) VALUES
    ('00000000-0000-0000-0000-000000000006', '6 furos', 2200),
    ('00000000-0000-0000-0000-000000000008', '8 furos', 1610);

CREATE TABLE motivo (
    id uuid PRIMARY KEY,
    tipo varchar(20) NOT NULL CHECK (tipo IN ('CORRECAO','CANCELAMENTO')),
    descricao varchar(120) NOT NULL,
    exige_texto boolean NOT NULL DEFAULT false,
    ativo boolean NOT NULL DEFAULT true,
    UNIQUE (tipo, descricao)
);
INSERT INTO motivo (id, tipo, descricao, exige_texto) VALUES
    (gen_random_uuid(), 'CORRECAO',     'Contagem errada',      false),
    (gen_random_uuid(), 'CORRECAO',     'Digitação errada',     false),
    (gen_random_uuid(), 'CORRECAO',     'Outro',                true),
    (gen_random_uuid(), 'CANCELAMENTO', 'Lançamento duplicado', false),
    (gen_random_uuid(), 'CANCELAMENTO', 'Data errada',          false),
    (gen_random_uuid(), 'CANCELAMENTO', 'Etapa errada',         false),
    (gen_random_uuid(), 'CANCELAMENTO', 'Fornada errada',       false),
    (gen_random_uuid(), 'CANCELAMENTO', 'Outro',                true);

CREATE TABLE fornada (
    id uuid PRIMARY KEY,
    id_unidade uuid NOT NULL REFERENCES unidade(id),
    numero integer NOT NULL CHECK (numero > 0),
    mes date NOT NULL CHECK (extract(day FROM mes) = 1),
    situacao varchar(20) NOT NULL DEFAULT 'EM_ANDAMENTO' CHECK (situacao IN ('EM_ANDAMENTO','CONCLUIDA')),
    enforna_concluida_em timestamptz,
    inicio_esquente timestamptz,
    inicio_queima timestamptz,
    fim_queima timestamptz,
    concluida_em timestamptz,
    versao bigint NOT NULL DEFAULT 0,
    UNIQUE (id_unidade, mes, numero),
    CHECK ((situacao = 'CONCLUIDA') = (concluida_em IS NOT NULL))
);

-- Columns shared by the three stages. chave_envio/impressao_envio make a resent form idempotent;
-- every row created by one form submission shares the same key.
CREATE TABLE maromba (
    id uuid PRIMARY KEY,
    id_unidade uuid NOT NULL REFERENCES unidade(id),
    id_formato uuid NOT NULL REFERENCES formato(id),
    id_autor uuid NOT NULL REFERENCES usuario(id),
    id_validador uuid REFERENCES usuario(id),
    data date NOT NULL,
    quantidade integer NOT NULL CHECK (quantidade > 0),
    situacao varchar(20) NOT NULL CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem varchar(20) NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em timestamptz NOT NULL,
    validado_em timestamptz,
    versao bigint NOT NULL DEFAULT 0,
    chave_envio uuid NOT NULL,
    impressao_envio varchar(64) NOT NULL,
    UNIQUE (chave_envio, id_formato),
    CHECK (situacao <> 'PENDENTE' OR (validado_em IS NULL AND id_validador IS NULL)),
    CHECK (situacao <> 'VALIDADO' OR (validado_em IS NOT NULL AND id_validador IS NOT NULL))
);

CREATE TABLE enforna (
    id uuid PRIMARY KEY,
    id_fornada uuid NOT NULL REFERENCES fornada(id),
    id_formato uuid NOT NULL REFERENCES formato(id),
    id_autor uuid NOT NULL REFERENCES usuario(id),
    id_validador uuid REFERENCES usuario(id),
    data date NOT NULL,
    linhas integer NOT NULL CHECK (linhas > 0),
    tijolos_por_linha integer NOT NULL CHECK (tijolos_por_linha > 0),
    quantidade integer NOT NULL CHECK (quantidade > 0),
    situacao varchar(20) NOT NULL CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem varchar(20) NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em timestamptz NOT NULL,
    validado_em timestamptz,
    versao bigint NOT NULL DEFAULT 0,
    chave_envio uuid NOT NULL,
    impressao_envio varchar(64) NOT NULL,
    UNIQUE (chave_envio, id_formato),
    CHECK (situacao <> 'PENDENTE' OR (validado_em IS NULL AND id_validador IS NULL)),
    CHECK (situacao <> 'VALIDADO' OR (validado_em IS NOT NULL AND id_validador IS NOT NULL))
);

CREATE TABLE desenforna (
    id uuid PRIMARY KEY,
    id_fornada uuid NOT NULL REFERENCES fornada(id),
    id_formato uuid NOT NULL REFERENCES formato(id),
    id_autor uuid NOT NULL REFERENCES usuario(id),
    id_validador uuid REFERENCES usuario(id),
    data date NOT NULL,
    linhas integer NOT NULL CHECK (linhas > 0),
    primeira integer NOT NULL CHECK (primeira >= 0),
    segunda integer NOT NULL CHECK (segunda >= 0),
    cru integer NOT NULL CHECK (cru >= 0),
    situacao varchar(20) NOT NULL CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem varchar(20) NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em timestamptz NOT NULL,
    validado_em timestamptz,
    versao bigint NOT NULL DEFAULT 0,
    chave_envio uuid NOT NULL,
    impressao_envio varchar(64) NOT NULL,
    UNIQUE (chave_envio, id_formato),
    CHECK (primeira + segunda + cru > 0),
    CHECK (situacao <> 'PENDENTE' OR (validado_em IS NULL AND id_validador IS NULL)),
    CHECK (situacao <> 'VALIDADO' OR (validado_em IS NOT NULL AND id_validador IS NOT NULL))
);

-- RN09: one record per day and format (and furnace load); a cancelled record frees the key.
CREATE UNIQUE INDEX maromba_unica ON maromba (id_unidade, id_formato, data) WHERE situacao <> 'CANCELADO';
CREATE UNIQUE INDEX enforna_unica ON enforna (id_fornada, id_formato, data) WHERE situacao <> 'CANCELADO';
CREATE UNIQUE INDEX desenforna_unica ON desenforna (id_fornada, id_formato, data) WHERE situacao <> 'CANCELADO';
CREATE INDEX maromba_autor_data ON maromba (id_autor, data DESC);
CREATE INDEX maromba_painel ON maromba (situacao, data, id_unidade);
CREATE INDEX enforna_fornada ON enforna (id_fornada, data);
CREATE INDEX desenforna_fornada ON desenforna (id_fornada, data);

CREATE TABLE inventario_inicial (
    id uuid PRIMARY KEY,
    id_unidade uuid NOT NULL REFERENCES unidade(id),
    id_fornada uuid REFERENCES fornada(id),
    id_formato uuid NOT NULL REFERENCES formato(id),
    data_hora timestamptz NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('AGUARDANDO_QUEIMA','ASSADO','ACABADO_1A','ACABADO_2A')),
    linhas integer CHECK (linhas >= 0),
    quantidade integer NOT NULL CHECK (quantidade >= 0),
    -- Bricks in the kiln belong to a furnace load; finished product does not.
    CHECK ((estado IN ('AGUARDANDO_QUEIMA','ASSADO')) = (id_fornada IS NOT NULL))
);

-- Move each old Maromba row into one row per format with production. The row that keeps the
-- old id (8F when present, otherwise 6F) keeps its audit history.
INSERT INTO maromba (id, id_unidade, id_formato, id_autor, id_validador, data, quantidade, situacao,
                     criado_em, validado_em, versao, chave_envio, impressao_envio)
SELECT id, unidade_id, '00000000-0000-0000-0000-000000000008', autor_id, validador_id, data_producao,
       quantidade_8f, situacao, criado_em, validado_em, versao, chave_envio, impressao_envio
FROM lancamento WHERE quantidade_8f > 0;
INSERT INTO maromba (id, id_unidade, id_formato, id_autor, id_validador, data, quantidade, situacao,
                     criado_em, validado_em, versao, chave_envio, impressao_envio)
SELECT CASE WHEN quantidade_8f > 0 THEN gen_random_uuid() ELSE id END, unidade_id,
       '00000000-0000-0000-0000-000000000006', autor_id, validador_id, data_producao,
       quantidade_6f, situacao, criado_em, validado_em, versao, chave_envio, impressao_envio
FROM lancamento WHERE quantidade_6f > 0;

-- The audit trail now points to three tables, so it names the table instead of using a foreign key.
-- DDL does not fire the immutability trigger; existing rows get tabela = 'maromba' from the default.
ALTER TABLE auditoria DROP CONSTRAINT auditoria_lancamento_id_fkey;
DROP INDEX auditoria_lancamento;
ALTER TABLE auditoria RENAME COLUMN lancamento_id TO id_registro;
ALTER TABLE auditoria ALTER COLUMN id_registro DROP NOT NULL;
ALTER TABLE auditoria RENAME COLUMN usuario_id TO id_usuario;
ALTER TABLE auditoria RENAME COLUMN ocorrido_em TO data_hora;
ALTER TABLE auditoria RENAME COLUMN valor_anterior TO antes;
ALTER TABLE auditoria RENAME COLUMN valor_novo TO depois;
ALTER TABLE auditoria ALTER COLUMN depois DROP NOT NULL;
ALTER TABLE auditoria ADD COLUMN tabela varchar(30) NOT NULL DEFAULT 'maromba';
ALTER TABLE auditoria ALTER COLUMN tabela DROP DEFAULT;
ALTER TABLE auditoria ADD COLUMN id_motivo uuid REFERENCES motivo(id);
ALTER TABLE auditoria ADD COLUMN texto_motivo text;
DO $$
DECLARE c record;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'auditoria'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) LIKE '%VALIDACAO%'
    LOOP
        EXECUTE format('ALTER TABLE auditoria DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;
-- EDICAO is the former name of CORRECAO and stays valid for events recorded before this migration.
ALTER TABLE auditoria ADD CONSTRAINT auditoria_acao_valida
    CHECK (acao IN ('CRIACAO','EDICAO','CORRECAO','VALIDACAO','CANCELAMENTO','IMPORTACAO'));
CREATE INDEX auditoria_registro ON auditoria (tabela, id_registro, data_hora);

DROP TABLE lancamento;

GRANT SELECT, INSERT, UPDATE ON formato, motivo, fornada, maromba, enforna, desenforna, inventario_inicial TO socre_app;
