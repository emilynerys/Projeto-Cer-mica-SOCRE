package br.com.socre.producao.producao;

import br.com.socre.producao.cadastro.Unidade;
import jakarta.persistence.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

// A firing cycle (RN06), numbered within the month per unit; at most two are in progress (RN08).
@Entity @Table(name = "fornada")
public class Fornada {
    public enum Situacao { EM_ANDAMENTO, CONCLUIDA }
    public enum Marco { ENFORNA_CONCLUIDA, INICIO_ESQUENTE, INICIO_QUEIMA, FIM_QUEIMA }
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "id_unidade", nullable = false) private Unidade unidade;
    @Column(nullable = false) private int numero;
    @Column(nullable = false) private LocalDate mes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Situacao situacao;
    @Column(name = "enforna_concluida_em") private Instant enfornaConcluidaEm;
    @Column(name = "inicio_esquente") private Instant inicioEsquente;
    @Column(name = "inicio_queima") private Instant inicioQueima;
    @Column(name = "fim_queima") private Instant fimQueima;
    @Column(name = "concluida_em") private Instant concluidaEm;
    @Version private long versao;
    protected Fornada() {}
    public Fornada(Unidade unidade, YearMonth mes, int numero) {
        this.id = UUID.randomUUID(); this.unidade = unidade; this.mes = mes.atDay(1); this.numero = numero;
        this.situacao = Situacao.EM_ANDAMENTO;
    }
    public void registrar(Marco marco, Instant quando) {
        switch (marco) {
            case ENFORNA_CONCLUIDA -> enfornaConcluidaEm = quando;
            case INICIO_ESQUENTE -> inicioEsquente = quando;
            case INICIO_QUEIMA -> inicioQueima = quando;
            case FIM_QUEIMA -> fimQueima = quando;
        }
    }
    public void concluir(Instant agora) { situacao = Situacao.CONCLUIDA; concluidaEm = agora; }
    public boolean isEmAndamento() { return situacao == Situacao.EM_ANDAMENTO; }
    public boolean isRecebendoEnforna() { return isEmAndamento() && enfornaConcluidaEm == null; }
    /** "2ª fornada de outubro/2026" (RN06). */
    public String getIdentificacao() {
        var nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"));
        return numero + "ª fornada de " + nomeMes + "/" + mes.getYear();
    }
    public UUID getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public int getNumero() { return numero; }
    public LocalDate getMes() { return mes; }
    public Situacao getSituacao() { return situacao; }
    public Instant getEnfornaConcluidaEm() { return enfornaConcluidaEm; }
    public Instant getInicioEsquente() { return inicioEsquente; }
    public Instant getInicioQueima() { return inicioQueima; }
    public Instant getFimQueima() { return fimQueima; }
    public Instant getConcluidaEm() { return concluidaEm; }
    public long getVersao() { return versao; }
}
