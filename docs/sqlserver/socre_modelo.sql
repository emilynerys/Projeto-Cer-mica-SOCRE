/* =====================================================================
   SOCRE: modelo do banco para SQL Server (estudo e documentação do TCC)
   ---------------------------------------------------------------------
   O sistema SOCRE roda em PostgreSQL (migrações Flyway em
   src/main/resources/db/migration). Este script recria o mesmo modelo
   em SQL Server para visualizar, gerar o diagrama e treinar consultas.

   Como usar: abra no SQL Server Management Studio (SSMS) e aperte F5.
   O script apaga e recria o banco SOCRE_estudo a cada execução.
   ===================================================================== */

USE master;
GO
IF DB_ID('SOCRE_estudo') IS NOT NULL
BEGIN
    ALTER DATABASE SOCRE_estudo SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE SOCRE_estudo;
END
GO
CREATE DATABASE SOCRE_estudo;
GO
USE SOCRE_estudo;
GO

/* ---------- 1. Cadastros ---------- */

CREATE TABLE unidade (
    id            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    nome          NVARCHAR(120)    NOT NULL,
    linhas_forno  INT              NOT NULL CHECK (linhas_forno > 0),
    ativa         BIT              NOT NULL DEFAULT 1
);

CREATE TABLE formato (
    id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    nome               NVARCHAR(60)     NOT NULL UNIQUE,
    tijolos_por_linha  INT              NOT NULL CHECK (tijolos_por_linha > 0),
    ativo              BIT              NOT NULL DEFAULT 1
);

CREATE TABLE usuario (
    id          UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    id_unidade  UNIQUEIDENTIFIER NULL REFERENCES unidade(id),
    nome        NVARCHAR(120)    NOT NULL,
    email       NVARCHAR(180)    NOT NULL UNIQUE,
    senha_hash  NVARCHAR(100)    NOT NULL,
    perfil      NVARCHAR(30)     NOT NULL
        CHECK (perfil IN ('REGISTRO_PRODUCAO','GESTOR_PRODUCAO','GESTOR_GERAL','ADMINISTRADOR')),
    ativo       BIT              NOT NULL DEFAULT 1,
    versao_acesso INT            NOT NULL DEFAULT 0,
    -- Quem registra produção precisa estar ligado a uma unidade
    CONSTRAINT usuario_registro_tem_unidade CHECK (perfil <> 'REGISTRO_PRODUCAO' OR id_unidade IS NOT NULL)
);

CREATE TABLE motivo (
    id           UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    tipo         NVARCHAR(20)     NOT NULL CHECK (tipo IN ('CORRECAO','CANCELAMENTO')),
    descricao    NVARCHAR(120)    NOT NULL,
    exige_texto  BIT              NOT NULL DEFAULT 0,
    ativo        BIT              NOT NULL DEFAULT 1,
    CONSTRAINT motivo_unico UNIQUE (tipo, descricao)
);

/* ---------- 2. Forno ---------- */

CREATE TABLE fornada (
    id                    UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    id_unidade            UNIQUEIDENTIFIER NOT NULL REFERENCES unidade(id),
    numero                INT              NOT NULL CHECK (numero > 0),
    mes                   DATE             NOT NULL,   -- sempre o dia 1 do mês: 2026-08-01
    situacao              NVARCHAR(20)     NOT NULL DEFAULT 'EM_ANDAMENTO'
        CHECK (situacao IN ('EM_ANDAMENTO','CONCLUIDA')),
    enforna_concluida_em  DATETIME2        NULL,
    inicio_esquente       DATETIME2        NULL,
    inicio_queima         DATETIME2        NULL,
    fim_queima            DATETIME2        NULL,
    concluida_em          DATETIME2        NULL,
    versao                ROWVERSION,
    CONSTRAINT fornada_numero_no_mes UNIQUE (id_unidade, mes, numero),
    -- Concluída sempre tem data de conclusão; em andamento nunca tem
    CONSTRAINT fornada_concluida_tem_data CHECK (
        (situacao = 'CONCLUIDA' AND concluida_em IS NOT NULL) OR (situacao = 'EM_ANDAMENTO' AND concluida_em IS NULL))
);
GO

/* ---------- 3. As três etapas ---------- */
-- Cada registro é UM formato em UM dia.

CREATE TABLE maromba (
    id            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    id_unidade    UNIQUEIDENTIFIER NOT NULL REFERENCES unidade(id),
    id_formato    UNIQUEIDENTIFIER NOT NULL REFERENCES formato(id),
    id_autor      UNIQUEIDENTIFIER NOT NULL REFERENCES usuario(id),
    id_validador  UNIQUEIDENTIFIER NULL     REFERENCES usuario(id),
    data          DATE             NOT NULL,
    quantidade    INT              NOT NULL CHECK (quantidade > 0),
    situacao      NVARCHAR(20)     NOT NULL DEFAULT 'PENDENTE'
        CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem        NVARCHAR(20)     NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em     DATETIME2        NOT NULL DEFAULT SYSDATETIME(),
    validado_em   DATETIME2        NULL,
    versao        ROWVERSION
);

CREATE TABLE enforna (
    id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    id_fornada         UNIQUEIDENTIFIER NOT NULL REFERENCES fornada(id),
    id_formato         UNIQUEIDENTIFIER NOT NULL REFERENCES formato(id),
    id_autor           UNIQUEIDENTIFIER NOT NULL REFERENCES usuario(id),
    id_validador       UNIQUEIDENTIFIER NULL     REFERENCES usuario(id),
    data               DATE             NOT NULL,
    linhas             INT              NOT NULL CHECK (linhas > 0),
    tijolos_por_linha  INT              NOT NULL CHECK (tijolos_por_linha > 0),  -- valor usado naquele dia
    quantidade         INT              NOT NULL CHECK (quantidade > 0),
    situacao           NVARCHAR(20)     NOT NULL DEFAULT 'PENDENTE'
        CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem             NVARCHAR(20)     NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em     DATETIME2        NOT NULL DEFAULT SYSDATETIME(),
    validado_em   DATETIME2        NULL,
    versao             ROWVERSION
);

CREATE TABLE desenforna (
    id            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    id_fornada    UNIQUEIDENTIFIER NOT NULL REFERENCES fornada(id),
    id_formato    UNIQUEIDENTIFIER NOT NULL REFERENCES formato(id),
    id_autor      UNIQUEIDENTIFIER NOT NULL REFERENCES usuario(id),
    id_validador  UNIQUEIDENTIFIER NULL     REFERENCES usuario(id),
    data          DATE             NOT NULL,
    linhas        INT              NOT NULL CHECK (linhas > 0),
    primeira      INT              NOT NULL CHECK (primeira >= 0),
    segunda       INT              NOT NULL CHECK (segunda >= 0),
    cru           INT              NOT NULL CHECK (cru >= 0),
    situacao      NVARCHAR(20)     NOT NULL DEFAULT 'PENDENTE'
        CHECK (situacao IN ('PENDENTE','VALIDADO','CANCELADO')),
    origem        NVARCHAR(20)     NOT NULL DEFAULT 'DIGITADO' CHECK (origem IN ('DIGITADO','IMPORTADO')),
    criado_em     DATETIME2        NOT NULL DEFAULT SYSDATETIME(),
    validado_em   DATETIME2        NULL,
    versao        ROWVERSION,
    CONSTRAINT desenforna_tem_tijolo CHECK (primeira + segunda + cru > 0)
);
GO

-- Validado sempre tem validador (vale para as três etapas)
ALTER TABLE maromba    ADD CONSTRAINT maromba_validacao    CHECK (situacao <> 'VALIDADO' OR id_validador IS NOT NULL);
ALTER TABLE enforna    ADD CONSTRAINT enforna_validacao    CHECK (situacao <> 'VALIDADO' OR id_validador IS NOT NULL);
ALTER TABLE desenforna ADD CONSTRAINT desenforna_validacao CHECK (situacao <> 'VALIDADO' OR id_validador IS NOT NULL);

-- RN09: um registro por dia + formato (+ fornada). Cancelado não conta, então libera a vaga.
CREATE UNIQUE INDEX maromba_unica    ON maromba    (id_unidade, id_formato, data) WHERE situacao IN ('PENDENTE','VALIDADO');
CREATE UNIQUE INDEX enforna_unica    ON enforna    (id_fornada, id_formato, data) WHERE situacao IN ('PENDENTE','VALIDADO');
CREATE UNIQUE INDEX desenforna_unica ON desenforna (id_fornada, id_formato, data) WHERE situacao IN ('PENDENTE','VALIDADO');
GO

/* ---------- 4. Ponto de partida (13/08/2026 18h) ---------- */

CREATE TABLE inventario_inicial (
    id          UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    id_unidade  UNIQUEIDENTIFIER NOT NULL REFERENCES unidade(id),
    id_fornada  UNIQUEIDENTIFIER NULL     REFERENCES fornada(id),
    id_formato  UNIQUEIDENTIFIER NOT NULL REFERENCES formato(id),
    data_hora   DATETIME2        NOT NULL,
    estado      NVARCHAR(20)     NOT NULL
        CHECK (estado IN ('AGUARDANDO_QUEIMA','ASSADO','ACABADO_1A','ACABADO_2A')),
    linhas      INT              NULL CHECK (linhas >= 0),
    quantidade  INT              NOT NULL CHECK (quantidade >= 0),
    -- No forno tem fornada; produto acabado não tem
    CONSTRAINT inventario_fornada_por_estado CHECK (
        (estado IN ('AGUARDANDO_QUEIMA','ASSADO') AND id_fornada IS NOT NULL)
     OR (estado IN ('ACABADO_1A','ACABADO_2A')    AND id_fornada IS NULL))
);

/* ---------- 5. Auditoria (ninguém altera nem apaga) ---------- */

CREATE TABLE auditoria (
    id            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
    id_usuario    UNIQUEIDENTIFIER NOT NULL REFERENCES usuario(id),
    id_motivo     UNIQUEIDENTIFIER NULL     REFERENCES motivo(id),
    tabela        NVARCHAR(30)     NOT NULL,   -- maromba, enforna, desenforna, inventario_inicial...
    id_registro   UNIQUEIDENTIFIER NULL,
    acao          NVARCHAR(20)     NOT NULL
        CHECK (acao IN ('CRIACAO','CORRECAO','VALIDACAO','CANCELAMENTO','IMPORTACAO')),
    data_hora     DATETIME2        NOT NULL DEFAULT SYSDATETIME(),
    antes         NVARCHAR(MAX)    NULL,
    depois        NVARCHAR(MAX)    NULL,
    texto_motivo  NVARCHAR(MAX)    NULL
);
GO
CREATE TRIGGER auditoria_imutavel ON auditoria INSTEAD OF UPDATE, DELETE AS
    THROW 50001, 'A trilha de auditoria não pode ser alterada nem excluída.', 1;
GO

/* =====================================================================
   DADOS DE EXEMPLO PARA TREINAR (valores inventados)
   ===================================================================== */

DECLARE @carolina UNIQUEIDENTIFIER = NEWID(),
        @f6 UNIQUEIDENTIFIER = NEWID(), @f8 UNIQUEIDENTIFIER = NEWID(),
        @admin UNIQUEIDENTIFIER = NEWID(), @registro UNIQUEIDENTIFIER = NEWID(), @gestor UNIQUEIDENTIFIER = NEWID(),
        @for1 UNIQUEIDENTIFIER = NEWID(), @for2 UNIQUEIDENTIFIER = NEWID(),
        @contagem UNIQUEIDENTIFIER = NEWID();

INSERT INTO unidade (id, nome, linhas_forno) VALUES (@carolina, N'Carolina', 46);

INSERT INTO formato (id, nome, tijolos_por_linha) VALUES
    (@f6, N'6 furos', 2200),
    (@f8, N'8 furos', 1610);

-- Contas fictícias: o hash abaixo não é uma senha real
INSERT INTO usuario (id, id_unidade, nome, email, senha_hash, perfil) VALUES
    (@admin,    NULL,      N'Administrador',      'admin@socre.local',    'hash-de-exemplo', 'ADMINISTRADOR'),
    (@registro, @carolina, N'Registro de Produção','registro@socre.local', 'hash-de-exemplo', 'REGISTRO_PRODUCAO'),
    (@gestor,   NULL,      N'Gestor de Produção', 'gestor@socre.local',   'hash-de-exemplo', 'GESTOR_PRODUCAO');

INSERT INTO motivo (id, tipo, descricao, exige_texto) VALUES
    (@contagem, 'CORRECAO',     N'Contagem errada',      0),
    (NEWID(),   'CORRECAO',     N'Digitação errada',     0),
    (NEWID(),   'CORRECAO',     N'Outro',                1),
    (NEWID(),   'CANCELAMENTO', N'Lançamento duplicado', 0),
    (NEWID(),   'CANCELAMENTO', N'Data errada',          0),
    (NEWID(),   'CANCELAMENTO', N'Etapa errada',         0),
    (NEWID(),   'CANCELAMENTO', N'Fornada errada',       0),
    (NEWID(),   'CANCELAMENTO', N'Outro',                1);

-- Numeração das fornadas no mês ainda a confirmar com a cerâmica
INSERT INTO fornada (id, id_unidade, numero, mes, enforna_concluida_em, inicio_esquente) VALUES
    (@for1, @carolina, 1, '2026-08-01', NULL, NULL),
    (@for2, @carolina, 2, '2026-08-01', '2026-08-14T10:00', '2026-08-14T11:00');

INSERT INTO inventario_inicial (id_unidade, id_fornada, id_formato, data_hora, estado, linhas, quantidade) VALUES
    (@carolina, @for1, @f8, '2026-08-13T18:00', 'ASSADO',            12, 19320),
    (@carolina, @for2, @f6, '2026-08-13T18:00', 'AGUARDANDO_QUEIMA',  4,  8800),
    (@carolina, @for2, @f8, '2026-08-13T18:00', 'AGUARDANDO_QUEIMA', 15, 24150),
    (@carolina, NULL,  @f8, '2026-08-13T18:00', 'ACABADO_1A',      NULL,  5000),
    (@carolina, NULL,  @f8, '2026-08-13T18:00', 'ACABADO_2A',      NULL,  7000),
    (@carolina, NULL,  @f6, '2026-08-13T18:00', 'ACABADO_2A',      NULL,   300);

INSERT INTO maromba (id_unidade, id_formato, id_autor, id_validador, data, quantidade, situacao, origem) VALUES
    (@carolina, @f8, @admin, @admin, '2026-08-14',  6000, 'VALIDADO', 'IMPORTADO'),
    (@carolina, @f8, @admin, @admin, '2026-08-15',  9500, 'VALIDADO', 'IMPORTADO');

INSERT INTO enforna (id_fornada, id_formato, id_autor, id_validador, data, linhas, tijolos_por_linha, quantidade, situacao, origem) VALUES
    (@for2, @f8, @admin, @admin, '2026-08-14', 4, 1610, 6440, 'VALIDADO', 'IMPORTADO'),
    (@for2, @f8, @admin, @admin, '2026-08-15', 3, 1610, 4830, 'VALIDADO', 'IMPORTADO');

INSERT INTO desenforna (id_fornada, id_formato, id_autor, id_validador, data, linhas, primeira, segunda, cru, situacao, origem) VALUES
    (@for1, @f8, @admin, @admin, '2026-08-14', 6, 8000, 1200,  400, 'VALIDADO', 'IMPORTADO'),
    (@for1, @f8, @admin, @admin, '2026-08-15', 4, 5400,  800,  200, 'VALIDADO', 'IMPORTADO');

INSERT INTO auditoria (id_usuario, tabela, acao, texto_motivo) VALUES
    (@admin, N'inventario_inicial', 'IMPORTACAO', N'Inventário de 13/08/2026 18h'),
    (@admin, N'maromba',            'IMPORTACAO', N'Carga histórica 14 e 15/08'),
    (@admin, N'enforna',            'IMPORTACAO', N'Carga histórica 14 e 15/08'),
    (@admin, N'desenforna',         'IMPORTACAO', N'Carga histórica 14 e 15/08');

UPDATE maromba    SET validado_em = '2026-08-16T08:00' WHERE situacao = 'VALIDADO';
UPDATE enforna    SET validado_em = '2026-08-16T08:00' WHERE situacao = 'VALIDADO';
UPDATE desenforna SET validado_em = '2026-08-16T08:00' WHERE situacao = 'VALIDADO';
GO

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
       f.nome                          AS formato,
       SUM(e.maromba)                  AS maromba,
       SUM(e.enforna)                  AS enforna,
       SUM(e.primeira)                 AS primeira,
       SUM(e.segunda)                  AS segunda,
       SUM(e.cru)                      AS cru,
       SUM(e.primeira + e.segunda)     AS acabado,
       SUM(e.maromba - e.enforna)      AS movimento_patio
  FROM etapas e
  JOIN formato f ON f.id = e.id_formato
 GROUP BY e.data, f.nome;
GO

-- Teste: rode só esta linha (selecione e aperte F5)
SELECT * FROM mapa_producao_diario ORDER BY data, formato;
