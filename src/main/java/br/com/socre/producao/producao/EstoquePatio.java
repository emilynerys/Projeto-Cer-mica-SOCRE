package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

// Burnt bricks counted in the yard on one day, by format and quality, as in the "Estoque assado" sheet.
@Entity @Table(name = "estoque_patio")
public class EstoquePatio {
    @Embeddable
    public static class Item {
        @ManyToOne @JoinColumn(name = "id_formato", nullable = false) private Formato formato;
        @Column(nullable = false) private int primeira;
        @Column(nullable = false) private int segunda;
        protected Item() {}
        public Item(Formato formato, int primeira, int segunda) { this.formato = formato; this.primeira = primeira; this.segunda = segunda; }
        public Formato getFormato() { return formato; }
        public int getPrimeira() { return primeira; }
        public int getSegunda() { return segunda; }
    }
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "id_unidade", nullable = false) private Unidade unidade;
    @ManyToOne @JoinColumn(name = "id_autor", nullable = false) private Usuario autor;
    @Column(nullable = false) private LocalDate data;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "estoque_patio_item", joinColumns = @JoinColumn(name = "id_estoque"))
    private List<Item> itens = new ArrayList<>();
    protected EstoquePatio() {}
    public EstoquePatio(Unidade unidade, Usuario autor, LocalDate data, List<Item> itens, Instant criadoEm) {
        this.id = UUID.randomUUID(); this.unidade = unidade; this.autor = autor; this.data = data; this.itens.addAll(itens); this.criadoEm = criadoEm;
    }
    public Optional<Item> item(UUID formato) { return itens.stream().filter(i -> i.getFormato().getId().equals(formato)).findFirst(); }
    public String retrato() {
        return "Estoque no pátio em " + data + " · " + String.join(" · ", itens.stream()
            .map(i -> i.getFormato().getSigla() + " 1ª " + i.getPrimeira() + " / 2ª " + i.getSegunda()).toList());
    }
    public UUID getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public Usuario getAutor() { return autor; }
    public LocalDate getData() { return data; }
    public Instant getCriadoEm() { return criadoEm; }
    public List<Item> getItens() { return itens; }
}
