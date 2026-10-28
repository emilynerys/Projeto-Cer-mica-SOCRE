package br.com.socre.producao.cadastro;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "unidade")
public class Unidade {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String nome;
    @Column(nullable = false) private boolean ativa;
    protected Unidade() {}
    public Unidade(UUID id, String nome) { this.id = id; this.nome = nome; this.ativa = true; }
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public boolean isAtiva() { return ativa; }
}
