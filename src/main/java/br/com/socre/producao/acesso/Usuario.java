package br.com.socre.producao.acesso;

import br.com.socre.producao.cadastro.Unidade;
import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name = "usuario")
public class Usuario {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String nome;
    @Column(nullable = false, length = 180) private String login;
    @Column(name = "senha_hash", nullable = false, length = 100) private String senhaHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Perfil perfil;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "id_unidade") private Unidade unidade;
    @Column(nullable = false) private boolean ativo;
    @Column(name = "versao_acesso", nullable = false) private int versaoAcesso;
    /** The password was set by the Administrador and must be changed at the next access. */
    @Column(name = "trocar_senha", nullable = false) private boolean trocarSenha;
    protected Usuario() {}
    public Usuario(UUID id, String nome, String login, String senhaHash, Perfil perfil, Unidade unidade) {
        this.id = id; this.nome = nome; this.login = login.toLowerCase(Locale.ROOT);
        this.senhaHash = senhaHash; this.perfil = perfil; this.unidade = unidade; this.ativo = true;
    }
    public void editar(String nome, String login, Perfil perfil, Unidade unidade, boolean ativo) {
        // A new access version ends the sessions open with the old profile or status.
        if (perfil != this.perfil || ativo != this.ativo) versaoAcesso++;
        this.nome = nome; this.login = login.toLowerCase(Locale.ROOT); this.perfil = perfil; this.unidade = unidade; this.ativo = ativo;
    }
    public void redefinirSenha(String senhaHash) { this.senhaHash = senhaHash; versaoAcesso++; trocarSenha = true; }
    public void exigirTrocaDeSenha() { trocarSenha = true; }
    public void trocarSenha(String senhaHash) { this.senhaHash = senhaHash; trocarSenha = false; }
    public boolean isTrocarSenha() { return trocarSenha; }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getLogin() { return login; }
    public String getSenhaHash() { return senhaHash; }
    public Perfil getPerfil() { return perfil; }
    public Unidade getUnidade() { return unidade; }
    public boolean isAtivo() { return ativo; }
    public int getVersaoAcesso() { return versaoAcesso; }
    public boolean podeEntrar() { return ativo && (perfil != Perfil.REGISTRO_PRODUCAO || (unidade != null && unidade.isAtiva())); }
}
