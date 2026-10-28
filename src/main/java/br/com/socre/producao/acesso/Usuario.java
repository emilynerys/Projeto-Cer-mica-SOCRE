package br.com.socre.producao.acesso;

import br.com.socre.producao.cadastro.Unidade;
import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name = "usuario")
public class Usuario {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String nome;
    @Column(nullable = false, length = 180) private String email;
    @Column(name = "senha_hash", nullable = false, length = 100) private String senhaHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Perfil perfil;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "unidade_id") private Unidade unidade;
    @Column(nullable = false) private boolean ativo;
    @Column(name = "versao_acesso", nullable = false) private int versaoAcesso;
    protected Usuario() {}
    public Usuario(UUID id, String nome, String email, String senhaHash, Perfil perfil, Unidade unidade) {
        this.id = id; this.nome = nome; this.email = email.toLowerCase(Locale.ROOT);
        this.senhaHash = senhaHash; this.perfil = perfil; this.unidade = unidade; this.ativo = true;
    }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Perfil getPerfil() { return perfil; }
    public Unidade getUnidade() { return unidade; }
    public boolean isAtivo() { return ativo; }
    public int getVersaoAcesso() { return versaoAcesso; }
    public boolean podeEntrar() { return ativo && (perfil != Perfil.REGISTRO_PRODUCAO || (unidade != null && unidade.isAtiva())); }
}
