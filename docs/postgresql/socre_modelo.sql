/* =====================================================================
   SOCRE: modelo do banco em PostgreSQL (estudo e documentação do TCC)
   ---------------------------------------------------------------------
   Banco separado do sistema: rode dentro de um banco vazio chamado
   socre_estudo. Ele não mexe no socre_dev nem no socre_test.
   O script apaga e recria as tabelas a cada execução.
   ===================================================================== */

DROP VIEW  IF EXISTS mapa_producao_diario;
DROP TABLE IF EXISTS auditoria, inventario_inicial, desenforna, enforna, maromba,
                     fornada, motivo, usuario, formato, unidade CASCADE;
DROP FUNCTION IF EXISTS impedir_mutacao_auditoria();

/* ---------- 1. Cadastros ---------- */

CREATE TABLE unidade (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome          varchar(120) NOT NULL,
    linhas_forno  integer      NOT NULL CHECK (linhas_forno > 0),
    ativa         boolean      NOT NULL DEFAULT true
);

CREATE TABLE formato (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    nome               varchar(60) NOT NULL UNIQUE,
    tijolos_por_linha  integer     NOT NULL CHECK (tijolos_por_linha > 0),
    ativo              boolean     NOT NULL DEFAULT true
);

CREATE TABLE usuario (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    id_unidade  uuid         REFERENCES unidade(id),
    nome        varchar(120) NOT NULL,
    email       varchar(180) NOT NULL UNIQUE,
    senha_hash  varchar(100) NOT NULL,
    perfil      varchar(30)  NOT NULL
        CHECK (perfil IN ('REGISTRO_PRODUCAO','GESTOR_PRODUCAO','GESTOR_GERAL','ADMINISTRADOR')),
    ativo       boolean      NOT NULL DEFAULT true,
    versao_acesso integer    NOT NULL DEFAULT 0,
    -- Quem registra produção precisa estar ligado a uma unidade
    CONSTRAINT usuario_registro_tem_unidade CHECK (perfil <> 'REGISTRO_PRODUCAO' OR id_unidade IS NOT NULL)
);

CREATE TABLE motivo (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo         varchar(20)  NOT NULL CHECK (tipo IN ('CORRECAO','CANCELAMENTO')),
    descricao    varchar(120) NOT NULL,
    exige_texto  boolean      NOT NULL DEFAULT false,
    ativo        boolean      NOT NULL DEFAULT true,
    UNIQUE (tipo, descricao)
);

/* ---------- 2. Forno ---------- */

CREATE TABLE fornada (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_unidade            uuid        NOT NULL REFERENCES unidade(id),
    numero                integer     NOT NULL CHECK (numero > 0),
    mes                   date        NOT NULL CHECK (extract(day FROM mes) = 1),  -- sempre o dia 1: 2026-08-01
    situacao              varchar(20) NOT NULL DEFAULT 'EM_ANDAMENTO'
        CHECK (situacao IN ('EM_ANDAMENTO','CONCLUIDA')),
    enforna_concluida_em  timestamp,
    inicio_esquente       timestamp,
    inicio_queima         timestamp,
    fim_queima            timestamp,
    concluida_em          timestamp,
    versao                bigint      NOT NULL DEFAULT 0,
    UNIQUE (id_unidade, mes, numero),
    -- Concluída sempre tem data de conclusão; em andamento nunca tem
    CHECK ((situacao = 'CONCLUIDA') = (concluida_em IS NOT NULL))
);

/* ---------- 3. As três etapas ---------- */
-- Cada registro é UM formato em UM dia.

CREATE TABLE maromba (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_unidade    uuid        NOT NULL REFERENCES unidade(id),
    id_formato    uuid        NOT NULL REFERENCES formato(id),
    id_autor      uuid        NOT NULL REFERENCES usuario(id),
    id_validador  uuid        REFERENCES usuario(id),
    data          date        NOT NULL,
    quantidade    integer     NOT NULL CHECK (quantidade > 0),
    situacao      varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem        varchar(20) NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em     timestamp   NOT NULL DEFAULT now(),
    validado_em   timestamp,
    versao        bigint      NOT NULL DEFAULT 0,
    CHECK (situacao <> 'VALIDADO' OR id_validador IS NOT NULL)
);

CREATE TABLE enforna (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_fornada         uuid        NOT NULL REFERENCES fornada(id),
    id_formato         uuid        NOT NULL REFERENCES formato(id),
    id_autor           uuid        NOT NULL REFERENCES usuario(id),
    id_validador       uuid        REFERENCES usuario(id),
    data               date        NOT NULL,
    linhas             integer     NOT NULL CHECK (linhas > 0),
    tijolos_por_linha  integer     NOT NULL CHECK (tijolos_por_linha > 0),  -- valor usado naquele dia
    quantidade         integer     NOT NULL CHECK (quantidade > 0),
    situacao           varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem             varchar(20) NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em          timestamp   NOT NULL DEFAULT now(),
    validado_em   timestamp,
    versao             bigint      NOT NULL DEFAULT 0,
    CHECK (situacao <> 'VALIDADO' OR id_validador IS NOT NULL)
);

CREATE TABLE desenforna (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_fornada    uuid        NOT NULL REFERENCES fornada(id),
    id_formato    uuid        NOT NULL REFERENCES formato(id),
    id_autor      uuid        NOT NULL REFERENCES usuario(id),
    id_validador  uuid        REFERENCES usuario(id),
    data          date        NOT NULL,
    linhas        integer     NOT NULL CHECK (linhas > 0),
    primeira      integer     NOT NULL CHECK (primeira >= 0),
    segunda       integer     NOT NULL CHECK (segunda >= 0),
    cru           integer     NOT NULL CHECK (cru >= 0),
    situacao      varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem        varchar(20) NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em     timestamp   NOT NULL DEFAULT now(),
    validado_em   timestamp,
    versao        bigint      NOT NULL DEFAULT 0,
    CHECK (primeira + segunda + cru > 0),
    CHECK (situacao <> 'VALIDADO' OR id_validador IS NOT NULL)
);

-- RN09: um registro por dia + formato (+ fornada). Cancelado não conta, então libera a vaga.
CREATE UNIQUE INDEX maromba_unica    ON maromba    (id_unidade, id_formato, data) WHERE situacao <> 'CANCELADO';
CREATE UNIQUE INDEX enforna_unica    ON enforna    (id_fornada, id_formato, data) WHERE situacao <> 'CANCELADO';
CREATE UNIQUE INDEX desenforna_unica ON desenforna (id_fornada, id_formato, data) WHERE situacao <> 'CANCELADO';

/* ---------- 4. Ponto de partida (13/08/2026 18h) ---------- */

CREATE TABLE inventario_inicial (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_unidade  uuid        NOT NULL REFERENCES unidade(id),
    id_fornada  uuid        REFERENCES fornada(id),
    id_formato  uuid        NOT NULL REFERENCES formato(id),
    data_hora   timestamp   NOT NULL,
    estado      varchar(20) NOT NULL CHECK (estado IN ('AGUARDANDO_QUEIMA','ASSADO','ACABADO_1A','ACABADO_2A')),
    linhas      integer     CHECK (linhas >= 0),
    quantidade  integer     NOT NULL CHECK (quantidade >= 0),
    -- No forno tem fornada; produto acabado não tem
    CHECK ((estado IN ('AGUARDANDO_QUEIMA','ASSADO')) = (id_fornada IS NOT NULL))
);

/* ---------- 5. Auditoria (ninguém altera nem apaga) ---------- */

CREATE TABLE auditoria (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario    uuid        NOT NULL REFERENCES usuario(id),
    id_motivo     uuid        REFERENCES motivo(id),
    tabela        varchar(30) NOT NULL,   -- maromba, enforna, desenforna, inventario_inicial...
    id_registro   uuid,
    acao          varchar(20) NOT NULL CHECK (acao IN ('CRIACAO','CORRECAO','VALIDACAO','CANCELAMENTO','IMPORTACAO')),
    data_hora     timestamp   NOT NULL DEFAULT now(),
    antes         text,
    depois        text,
    texto_motivo  text
);

CREATE FUNCTION impedir_mutacao_auditoria() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'A trilha de auditoria não pode ser alterada nem excluída';
END;
$$;
CREATE TRIGGER auditoria_imutavel BEFORE UPDATE OR DELETE OR TRUNCATE ON auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_auditoria();

/* =====================================================================
   DADOS DE EXEMPLO PARA TREINAR (valores inventados)
   ===================================================================== */

DO $$
DECLARE
    carolina uuid; f6 uuid; f8 uuid; admin uuid; for1 uuid; for2 uuid;
BEGIN
    INSERT INTO unidade (nome, linhas_forno) VALUES ('Carolina', 46) RETURNING id INTO carolina;
    INSERT INTO formato (nome, tijolos_por_linha) VALUES ('6 furos', 2200) RETURNING id INTO f6;
    INSERT INTO formato (nome, tijolos_por_linha) VALUES ('8 furos', 1610) RETURNING id INTO f8;

    -- Contas fictícias: o hash abaixo não é uma senha real
    INSERT INTO usuario (nome, email, senha_hash, perfil)
        VALUES ('Administrador', 'admin@socre.local', 'hash-de-exemplo', 'ADMINISTRADOR') RETURNING id INTO admin;
    INSERT INTO usuario (id_unidade, nome, email, senha_hash, perfil) VALUES
        (carolina, 'Registro de Produção', 'registro@socre.local', 'hash-de-exemplo', 'REGISTRO_PRODUCAO'),
        (NULL,     'Gestor de Produção',   'gestor@socre.local',   'hash-de-exemplo', 'GESTOR_PRODUCAO'),
        (NULL,     'Gestor Geral',         'geral@socre.local',    'hash-de-exemplo', 'GESTOR_GERAL');

    INSERT INTO motivo (tipo, descricao, exige_texto) VALUES
        ('CORRECAO',     'Contagem errada',      false),
        ('CORRECAO',     'Digitação errada',     false),
        ('CORRECAO',     'Outro',                true),
        ('CANCELAMENTO', 'Lançamento duplicado', false),
        ('CANCELAMENTO', 'Data errada',          false),
        ('CANCELAMENTO', 'Etapa errada',         false),
        ('CANCELAMENTO', 'Fornada errada',       false),
        ('CANCELAMENTO', 'Outro',                true);

    -- Numeração das fornadas no mês ainda a confirmar com a cerâmica
    INSERT INTO fornada (id_unidade, numero, mes) VALUES (carolina, 1, '2026-08-01') RETURNING id INTO for1;
    INSERT INTO fornada (id_unidade, numero, mes, enforna_concluida_em, inicio_esquente)
        VALUES (carolina, 2, '2026-08-01', '2026-08-14 10:00', '2026-08-14 11:00') RETURNING id INTO for2;

    INSERT INTO inventario_inicial (id_unidade, id_fornada, id_formato, data_hora, estado, linhas, quantidade) VALUES
        (carolina, for1, f8, '2026-08-13 18:00', 'ASSADO',            12, 19320),
        (carolina, for2, f6, '2026-08-13 18:00', 'AGUARDANDO_QUEIMA',  4,  8800),
        (carolina, for2, f8, '2026-08-13 18:00', 'AGUARDANDO_QUEIMA', 15, 24150),
        (carolina, NULL, f8, '2026-08-13 18:00', 'ACABADO_1A',      NULL,  5000),
        (carolina, NULL, f8, '2026-08-13 18:00', 'ACABADO_2A',      NULL,  7000),
        (carolina, NULL, f6, '2026-08-13 18:00', 'ACABADO_2A',      NULL,   300);

    INSERT INTO maromba (id_unidade, id_formato, id_autor, id_validador, data, quantidade, situacao, origem) VALUES
        (carolina, f8, admin, admin, '2026-08-14',  6000, 'VALIDADO', 'IMPORTADO'),
        (carolina, f8, admin, admin, '2026-08-15',  9500, 'VALIDADO', 'IMPORTADO');

    INSERT INTO enforna (id_fornada, id_formato, id_autor, id_validador, data, linhas, tijolos_por_linha, quantidade, situacao, origem) VALUES
        (for2, f8, admin, admin, '2026-08-14', 4, 1610, 6440, 'VALIDADO', 'IMPORTADO'),
        (for2, f8, admin, admin, '2026-08-15', 3, 1610, 4830, 'VALIDADO', 'IMPORTADO');

    INSERT INTO desenforna (id_fornada, id_formato, id_autor, id_validador, data, linhas, primeira, segunda, cru, situacao, origem) VALUES
        (for1, f8, admin, admin, '2026-08-14', 6, 8000, 1200,  400, 'VALIDADO', 'IMPORTADO'),
        (for1, f8, admin, admin, '2026-08-15', 4, 5400,  800,  200, 'VALIDADO', 'IMPORTADO');

    INSERT INTO auditoria (id_usuario, tabela, acao, texto_motivo) VALUES
        (admin, 'inventario_inicial', 'IMPORTACAO', 'Inventário de 13/08/2026 18h'),
        (admin, 'maromba',            'IMPORTACAO', 'Carga histórica 14 e 15/08'),
        (admin, 'enforna',            'IMPORTACAO', 'Carga histórica 14 e 15/08'),
        (admin, 'desenforna',         'IMPORTACAO', 'Carga histórica 14 e 15/08');

    UPDATE maromba    SET validado_em = '2026-08-16 08:00' WHERE situacao = 'VALIDADO';
    UPDATE enforna    SET validado_em = '2026-08-16 08:00' WHERE situacao = 'VALIDADO';
    UPDATE desenforna SET validado_em = '2026-08-16 08:00' WHERE situacao = 'VALIDADO';
END $$;

/* =====================================================================
   O MAPA DE PRODUÇÃO (consulta que junta as três etapas)
   ===================================================================== */

CREATE VIEW mapa_producao_diario AS
WITH etapas AS (
    SELECT data, id_formato, quantidade AS maromba, 0 AS enforna, 0 AS primeira, 0 AS segunda, 0 AS cru
      FROM maromba    WHERE situacao = 'VALIDADO'
    UNION ALL
    SELECT data, id_formato, 0, quantidade, 0, 0, 0
      FROM enforna    WHERE situacao = 'VALIDADO'
    UNION ALL
    SELECT data, id_formato, 0, 0, primeira, segunda, cru
      FROM desenforna WHERE situacao = 'VALIDADO'
)
SELECT e.data,
       f.nome                       AS formato,
       sum(e.maromba)               AS maromba,
       sum(e.enforna)               AS enforna,
       sum(e.primeira)              AS primeira,
       sum(e.segunda)               AS segunda,
       sum(e.cru)                   AS cru,
       sum(e.primeira + e.segunda)  AS acabado,
       sum(e.maromba - e.enforna)   AS movimento_patio
  FROM etapas e
  JOIN formato f ON f.id = e.id_formato
 GROUP BY e.data, f.nome;

-- Teste
SELECT * FROM mapa_producao_diario ORDER BY data, formato;
