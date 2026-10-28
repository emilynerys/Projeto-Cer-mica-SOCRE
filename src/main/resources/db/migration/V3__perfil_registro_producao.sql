-- Profile renamed on 12/10/2026: whoever types the production is the secretary,
-- so "Supervisor de Produção" becomes "Registro de Produção". Permissions are unchanged.

-- The V1 checks were created without names; drop every check on usuario that mentions the old value.
DO $$
DECLARE c record;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'usuario'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) LIKE '%SUPERVISOR%'
    LOOP
        EXECUTE format('ALTER TABLE usuario DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

UPDATE usuario SET perfil = 'REGISTRO_PRODUCAO' WHERE perfil = 'SUPERVISOR';

ALTER TABLE usuario ADD CONSTRAINT usuario_perfil_valido
    CHECK (perfil IN ('REGISTRO_PRODUCAO','GESTOR_PRODUCAO','GESTOR_GERAL','ADMINISTRADOR'));
ALTER TABLE usuario ADD CONSTRAINT usuario_registro_tem_unidade
    CHECK (perfil <> 'REGISTRO_PRODUCAO' OR unidade_id IS NOT NULL);

-- Demo account created only by the dev profile; it does not exist in a real deployment.
UPDATE usuario SET email = 'registro@socre.local', nome = 'Registro de demonstração'
WHERE lower(email) = 'supervisor@socre.local';

-- Stored sessions hold the serialized old profile value and can no longer be read.
DELETE FROM spring_session;
