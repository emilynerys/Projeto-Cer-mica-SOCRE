package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

// One format put into a furnace load on one day; the quantity is suggested by lines × bricks per line (RN12).
@Entity @Table(name = "enforna")
public class Enforna extends RegistroEtapa {
    @ManyToOne @JoinColumn(name = "id_fornada", nullable = false) private Fornada fornada;
    @Column(nullable = false) private int linhas;
    @Column(name = "tijolos_por_linha", nullable = false) private int tijolosPorLinha;
    @Column(nullable = false) private int quantidade;
    protected Enforna() {}
    public Enforna(Fornada fornada, Formato formato, Usuario autor, LocalDate data, int linhas, int tijolosPorLinha, int quantidade,
            UUID chave, String impressao, Instant agora) {
        super(formato, autor, data, chave, impressao, agora);
        this.fornada = fornada; this.linhas = linhas; this.tijolosPorLinha = tijolosPorLinha; this.quantidade = quantidade;
    }
    public void editar(LocalDate data, int linhas, int tijolosPorLinha, int quantidade) {
        mudarData(data); this.linhas = linhas; this.tijolosPorLinha = tijolosPorLinha; this.quantidade = quantidade;
    }
    @Override public String getEtapa() { return "Enforna"; }
    @Override public String getTabela() { return "enforna"; }
    @Override public Unidade getUnidade() { return fornada.getUnidade(); }
    @Override protected String valores() {
        return fornada.getIdentificacao() + " · " + getFormato().getNome() + ": " + linhas + " linhas × " + tijolosPorLinha + " = " + quantidade;
    }
    public Fornada getFornada() { return fornada; }
    public int getLinhas() { return linhas; }
    public int getTijolosPorLinha() { return tijolosPorLinha; }
    public int getQuantidade() { return quantidade; }
}
