-- The furnace load audit trail records the firing times (RN21) and the manager's conclusion (RN08).
ALTER TABLE auditoria DROP CONSTRAINT auditoria_acao_valida;
ALTER TABLE auditoria ADD CONSTRAINT auditoria_acao_valida
    CHECK (acao IN ('CRIACAO','EDICAO','CORRECAO','VALIDACAO','CANCELAMENTO','IMPORTACAO','MARCO','CONCLUSAO'));
