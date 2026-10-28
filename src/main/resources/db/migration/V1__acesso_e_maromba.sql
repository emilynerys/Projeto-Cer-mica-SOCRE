CREATE TABLE unidade (
    id uuid PRIMARY KEY,
    nome varchar(120) NOT NULL,
    ativa boolean NOT NULL DEFAULT true
);

CREATE TABLE usuario (
    id uuid PRIMARY KEY,
    nome varchar(120) NOT NULL,
    email varchar(180) NOT NULL,
    senha_hash varchar(100) NOT NULL,
    perfil varchar(30) NOT NULL CHECK (perfil IN ('SUPERVISOR','GESTOR_PRODUCAO','GESTOR_GERAL','ADMINISTRADOR')),
    unidade_id uuid REFERENCES unidade(id),
    ativo boolean NOT NULL DEFAULT true,
    versao_acesso integer NOT NULL DEFAULT 0,
    CHECK (perfil <> 'SUPERVISOR' OR unidade_id IS NOT NULL)
);
CREATE UNIQUE INDEX usuario_email_unico ON usuario (lower(email));

-- First delivery: Maromba only. Furnace stages are introduced by later migrations.
CREATE TABLE lancamento (
    id uuid PRIMARY KEY,
    unidade_id uuid NOT NULL REFERENCES unidade(id),
    autor_id uuid NOT NULL REFERENCES usuario(id),
    data_producao date NOT NULL,
    etapa varchar(20) NOT NULL CHECK (etapa = 'MAROMBA'),
    quantidade_6f integer NOT NULL CHECK (quantidade_6f >= 0),
    quantidade_8f integer NOT NULL CHECK (quantidade_8f >= 0),
    situacao varchar(20) NOT NULL CHECK (situacao IN ('PENDENTE','VALIDADO')),
    criado_em timestamptz NOT NULL,
    validado_em timestamptz,
    validador_id uuid REFERENCES usuario(id),
    versao bigint NOT NULL DEFAULT 0,
    chave_envio uuid NOT NULL UNIQUE,
    impressao_envio varchar(64) NOT NULL,
    CHECK (quantidade_6f > 0 OR quantidade_8f > 0),
    CHECK ((situacao = 'PENDENTE' AND validado_em IS NULL AND validador_id IS NULL)
        OR (situacao = 'VALIDADO' AND validado_em IS NOT NULL AND validador_id IS NOT NULL))
);
CREATE UNIQUE INDEX maromba_diaria_unica ON lancamento(unidade_id, data_producao) WHERE etapa = 'MAROMBA';
CREATE INDEX lancamento_autor_data ON lancamento(autor_id, data_producao DESC);
CREATE INDEX lancamento_painel ON lancamento(situacao, data_producao, unidade_id);

CREATE TABLE auditoria (
    id uuid PRIMARY KEY,
    lancamento_id uuid NOT NULL REFERENCES lancamento(id),
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    acao varchar(20) NOT NULL CHECK (acao IN ('CRIACAO','EDICAO','VALIDACAO')),
    ocorrido_em timestamptz NOT NULL,
    valor_anterior text,
    valor_novo text NOT NULL
);
CREATE INDEX auditoria_lancamento ON auditoria(lancamento_id, ocorrido_em);
CREATE FUNCTION impedir_mutacao_auditoria() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'A trilha de auditoria nao pode ser alterada ou excluida';
END;
$$;
CREATE TRIGGER auditoria_imutavel BEFORE UPDATE OR DELETE OR TRUNCATE ON auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_auditoria();

-- Runtime and migrations use separate credentials (see database setup script).
GRANT USAGE ON SCHEMA public TO socre_app;
GRANT SELECT, INSERT, UPDATE ON unidade, usuario, lancamento TO socre_app;
GRANT SELECT, INSERT ON auditoria TO socre_app;
