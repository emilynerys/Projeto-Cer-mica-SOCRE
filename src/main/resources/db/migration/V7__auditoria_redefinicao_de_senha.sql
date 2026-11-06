-- RF32: the trail records that a password was reset, never the password itself.
ALTER TABLE auditoria DROP CONSTRAINT auditoria_acao_valida;
ALTER TABLE auditoria ADD CONSTRAINT auditoria_acao_valida
    CHECK (acao IN ('CRIACAO','EDICAO','CORRECAO','VALIDACAO','CANCELAMENTO','IMPORTACAO','MARCO','CONCLUSAO','SENHA_REDEFINIDA'));
