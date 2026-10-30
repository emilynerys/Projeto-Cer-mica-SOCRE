package br.com.socre.producao.producao;

import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// One item of the unit's starting point (RN20): bricks in a furnace load, or finished product by quality.
@Entity @Table(name = "inventario_inicial")
public class InventarioInicial {
    public enum Estado {
        AGUARDANDO_QUEIMA, ASSADO, ACABADO_1A, ACABADO_2A;
        public boolean noForno() { return this == AGUARDANDO_QUEIMA || this == ASSADO; }
    }
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "id_unidade", nullable = false) private Unidade unidade;
    @ManyToOne @JoinColumn(name = "id_fornada") private Fornada fornada;
    @ManyToOne @JoinColumn(name = "id_formato", nullable = false) private Formato formato;
    @Column(name = "data_hora", nullable = false) private Instant dataHora;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Estado estado;
    private Integer linhas;
    @Column(nullable = false) private int quantidade;
    protected InventarioInicial() {}
    public InventarioInicial(Unidade unidade, Fornada fornada, Formato formato, Instant dataHora, Estado estado, Integer linhas, int quantidade) {
        this.id = UUID.randomUUID(); this.unidade = unidade; this.fornada = fornada; this.formato = formato;
        this.dataHora = dataHora; this.estado = estado; this.linhas = linhas; this.quantidade = quantidade;
    }
    public String retrato() {
        return estado + " · " + formato.getNome() + (fornada == null ? "" : " · " + fornada.getIdentificacao())
            + (linhas == null ? "" : " · " + linhas + " linhas") + " · " + quantidade;
    }
    public UUID getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public Fornada getFornada() { return fornada; }
    public Formato getFormato() { return formato; }
    public Instant getDataHora() { return dataHora; }
    public Estado getEstado() { return estado; }
    public Integer getLinhas() { return linhas; }
    public int getQuantidade() { return quantidade; }
}
