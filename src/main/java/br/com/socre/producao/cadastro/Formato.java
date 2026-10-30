package br.com.socre.producao.cadastro;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "formato")
public class Formato {
    // Ids of the two formats created by migration V4; the Administrator can add others (RN13).
    public static final UUID SEIS_FUROS = UUID.fromString("00000000-0000-0000-0000-000000000006");
    public static final UUID OITO_FUROS = UUID.fromString("00000000-0000-0000-0000-000000000008");
    @Id private UUID id;
    @Column(nullable = false, length = 60) private String nome;
    @Column(name = "tijolos_por_linha", nullable = false) private int tijolosPorLinha;
    @Column(nullable = false) private boolean ativo;
    protected Formato() {}
    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public int getTijolosPorLinha() { return tijolosPorLinha; }
    public boolean isAtivo() { return ativo; }
}
