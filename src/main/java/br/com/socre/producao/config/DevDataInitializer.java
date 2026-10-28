package br.com.socre.producao.config;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("dev")
public class DevDataInitializer implements CommandLineRunner {
    private final UnidadeRepository unidades; private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder; private final String senha;
    public DevDataInitializer(UnidadeRepository unidades, UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${socre.demo-password}") String senha) {
        this.unidades = unidades; this.usuarios = usuarios; this.encoder = encoder; this.senha = senha;
    }
    @Override @Transactional public void run(String... args) {
        if (usuarios.count() != 0) return;
        if (senha.length() < 12) throw new IllegalStateException("Configure SOCRE_DEMO_PASSWORD com ao menos 12 caracteres no perfil dev.");
        var unidade = unidades.save(new Unidade(UUID.randomUUID(), "Carolina · demonstração"));
        criar("supervisor", "Supervisor de demonstração", Perfil.SUPERVISOR, unidade);
        criar("gestor", "Gestor de demonstração", Perfil.GESTOR_PRODUCAO, null);
        criar("geral", "Gestão geral de demonstração", Perfil.GESTOR_GERAL, null);
        criar("admin", "Administrador de demonstração", Perfil.ADMINISTRADOR, null);
    }
    private void criar(String login, String nome, Perfil perfil, Unidade unidade) {
        usuarios.save(new Usuario(UUID.randomUUID(), nome, login + "@socre.local", encoder.encode(senha), perfil, unidade));
    }
}
