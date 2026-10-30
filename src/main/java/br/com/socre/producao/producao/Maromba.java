package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

// One format produced on one day at the Maromba (bricks go to the yard, no furnace load).
@Entity @Table(name = "maromba")
public class Maromba extends RegistroEtapa {
    @ManyToOne @JoinColumn(name = "id_unidade", nullable = false) private Unidade unidade;
    @Column(nullable = false) private int quantidade;
    protected Maromba() {}
    public Maromba(Unidade unidade, Formato formato, Usuario autor, LocalDate data, int quantidade, UUID chave, String impressao, Instant agora) {
        super(formato, autor, data, chave, impressao, agora);
        this.unidade = unidade; this.quantidade = quantidade;
    }
    public void editar(LocalDate data, int quantidade) { mudarData(data); this.quantidade = quantidade; }
    @Override public String getEtapa() { return "Maromba"; }
    @Override public String getTabela() { return "maromba"; }
    @Override public Unidade getUnidade() { return unidade; }
    @Override protected String valores() { return getFormato().getNome() + ": " + quantidade; }
    public int getQuantidade() { return quantidade; }
}
