package br.com.socre.producao.cadastro;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "unidade")
public class Unidade {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String nome;
    @Column(name = "linhas_forno", nullable = false) private int linhasForno;
    @Column(nullable = false) private boolean ativa;
    protected Unidade() {}
    public Unidade(UUID id, String nome, int linhasForno) { this.id = id; this.nome = nome; this.linhasForno = linhasForno; this.ativa = true; }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public int getLinhasForno() { return linhasForno; }
    public boolean isAtiva() { return ativa; }
}
