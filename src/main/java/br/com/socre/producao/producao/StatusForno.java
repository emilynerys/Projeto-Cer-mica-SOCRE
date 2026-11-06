package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

// One picture of the kiln, line by line, as the operator writes it on the "Status do Forno" paper.
@Entity @Table(name = "status_forno")
public class StatusForno {
    /** The letters of the paper; an empty line has no state. */
    public enum Estado {
        ESQUENTE("E", "Esquente"), QUEIMA("Q", "Queima"), ASSADO("A", "Assado"), CRU("C", "Cru"), DANIFICADO("D", "Danificado");
        private final String letra, descricao;
        Estado(String letra, String descricao) { this.letra = letra; this.descricao = descricao; }
        public String getLetra() { return letra; }
        public String getDescricao() { return descricao; }
    }
    @Embeddable
    public static class Linha {
        @Column(nullable = false) private int linha;
        @ManyToOne @JoinColumn(name = "id_formato") private Formato formato;
        @Enumerated(EnumType.STRING) @Column(length = 12) private Estado estado;
        @Column(nullable = false) private int quantidade;
        protected Linha() {}
        public Linha(int linha, Formato formato, Estado estado) {
            this.linha = linha; this.formato = formato; this.estado = estado;
            this.quantidade = formato == null ? 0 : formato.getTijolosPorLinha();
        }
        public int getLinha() { return linha; }
        public Formato getFormato() { return formato; }
        public Estado getEstado() { return estado; }
        public int getQuantidade() { return quantidade; }
        public boolean isVazia() { return formato == null; }
    }
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "id_unidade", nullable = false) private Unidade unidade;
    @ManyToOne @JoinColumn(name = "id_autor", nullable = false) private Usuario autor;
    @Column(name = "data_hora", nullable = false) private Instant dataHora;
    @Column(length = 300) private String observacao;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "status_forno_linha", joinColumns = @JoinColumn(name = "id_status")) @OrderBy("linha")
    private List<Linha> linhas = new ArrayList<>();
    protected StatusForno() {}
    public StatusForno(Unidade unidade, Usuario autor, Instant dataHora, String observacao, List<Linha> linhas, Instant criadoEm) {
        this.id = UUID.randomUUID(); this.unidade = unidade; this.autor = autor; this.dataHora = dataHora;
        this.observacao = observacao; this.linhas.addAll(linhas); this.criadoEm = criadoEm;
    }
    public String retrato() {
        var cheias = linhas.stream().filter(l -> !l.isVazia()).map(l -> l.getLinha() + ":" + l.getFormato().getSigla() + l.getEstado().getLetra()).toList();
        return "Status do Forno · " + (cheias.isEmpty() ? "forno vazio" : String.join(" ", cheias)) + (observacao == null ? "" : " · " + observacao);
    }
    public UUID getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public Usuario getAutor() { return autor; }
    public Instant getDataHora() { return dataHora; }
    public String getObservacao() { return observacao; }
    public Instant getCriadoEm() { return criadoEm; }
    public List<Linha> getLinhas() { return linhas; }
}
