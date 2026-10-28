package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.Unidade;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "lancamento")
public class Lancamento {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "unidade_id", nullable = false) private Unidade unidade;
    @ManyToOne @JoinColumn(name = "autor_id", nullable = false) private Usuario autor;
    @Column(name = "data_producao", nullable = false) private LocalDate dataProducao;
    @Column(nullable = false, length = 20) private String etapa;
    @Column(name = "quantidade_6f", nullable = false) private int quantidade6f;
    @Column(name = "quantidade_8f", nullable = false) private int quantidade8f;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Situacao situacao;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "validado_em") private Instant validadoEm;
    @ManyToOne @JoinColumn(name = "validador_id") private Usuario validador;
    @Version private long versao;
    @Column(name = "chave_envio", nullable = false, updatable = false) private UUID chaveEnvio;
    @Column(name = "impressao_envio", nullable = false, updatable = false, length = 64) private String impressaoEnvio;
    protected Lancamento() {}
    public Lancamento(Unidade unidade, Usuario autor, LocalDate data, int q6, int q8, UUID chave, String impressao, Instant agora) {
        this.id = UUID.randomUUID(); this.unidade = unidade; this.autor = autor; this.dataProducao = data;
        this.quantidade6f = q6; this.quantidade8f = q8; this.etapa = "MAROMBA";
        this.situacao = Situacao.PENDENTE; this.criadoEm = agora; this.chaveEnvio = chave; this.impressaoEnvio = impressao;
    }
    public void editar(LocalDate data, int q6, int q8) { dataProducao = data; quantidade6f = q6; quantidade8f = q8; }
    public void validar(Usuario gestor, Instant agora) { situacao = Situacao.VALIDADO; validador = gestor; validadoEm = agora; }
    public String retrato() {
        return "Data: " + dataProducao + " · 6 furos: " + quantidade6f + " · 8 furos: " + quantidade8f + " · " + situacao.getDescricao();
    }
    public UUID getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public Usuario getAutor() { return autor; }
    public LocalDate getDataProducao() { return dataProducao; }
    public String getEtapa() { return etapa; }
    public int getQuantidade6f() { return quantidade6f; }
    public int getQuantidade8f() { return quantidade8f; }
    public long getTotal() { return (long) quantidade6f + quantidade8f; }
    public Situacao getSituacao() { return situacao; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getValidadoEm() { return validadoEm; }
    public Usuario getValidador() { return validador; }
    public long getVersao() { return versao; }
    public UUID getChaveEnvio() { return chaveEnvio; }
    public String getImpressaoEnvio() { return impressaoEnvio; }
}
