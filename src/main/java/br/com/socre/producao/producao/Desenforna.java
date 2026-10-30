package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

// One format taken out of a furnace load on one day: 1st and 2nd quality plus the Cru that left the kiln (RN10).
@Entity @Table(name = "desenforna")
public class Desenforna extends RegistroEtapa {
    @ManyToOne @JoinColumn(name = "id_fornada", nullable = false) private Fornada fornada;
    @Column(nullable = false) private int linhas;
    @Column(nullable = false) private int primeira;
    @Column(nullable = false) private int segunda;
    @Column(nullable = false) private int cru;
    protected Desenforna() {}
    public Desenforna(Fornada fornada, Formato formato, Usuario autor, LocalDate data, int linhas, int primeira, int segunda, int cru,
            UUID chave, String impressao, Instant agora) {
        super(formato, autor, data, chave, impressao, agora);
        this.fornada = fornada; this.linhas = linhas; this.primeira = primeira; this.segunda = segunda; this.cru = cru;
    }
    public void editar(LocalDate data, int linhas, int primeira, int segunda, int cru) {
        mudarData(data); this.linhas = linhas; this.primeira = primeira; this.segunda = segunda; this.cru = cru;
    }
    @Override public String getEtapa() { return "Desenforna"; }
    @Override public String getTabela() { return "desenforna"; }
    @Override public Unidade getUnidade() { return fornada.getUnidade(); }
    @Override protected String valores() {
        return fornada.getIdentificacao() + " · " + getFormato().getNome() + ": " + linhas + " linhas · 1ª " + primeira + " · 2ª " + segunda + " · Cru " + cru;
    }
    public int getTotal() { return primeira + segunda + cru; }
    /** Finished production is 1st + 2nd only; Cru stays apart (RN10). */
    public int getAcabado() { return primeira + segunda; }
    public Fornada getFornada() { return fornada; }
    public int getLinhas() { return linhas; }
    public int getPrimeira() { return primeira; }
    public int getSegunda() { return segunda; }
    public int getCru() { return cru; }
}
