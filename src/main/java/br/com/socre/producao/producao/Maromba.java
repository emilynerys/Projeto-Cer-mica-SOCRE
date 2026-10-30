package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

// One format produced on one day at the Maromba (bricks go to the yard, no furnace load).
@Entity @Table(name = "maromba")
public class Maromba {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "id_unidade", nullable = false) private Unidade unidade;
    @ManyToOne @JoinColumn(name = "id_formato", nullable = false) private Formato formato;
    @ManyToOne @JoinColumn(name = "id_autor", nullable = false) private Usuario autor;
    @ManyToOne @JoinColumn(name = "id_validador") private Usuario validador;
    @Column(nullable = false) private LocalDate data;
    @Column(nullable = false) private int quantidade;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Situacao situacao;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Origem origem;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "validado_em") private Instant validadoEm;
    @Version private long versao;
    @Column(name = "chave_envio", nullable = false, updatable = false) private UUID chaveEnvio;
    @Column(name = "impressao_envio", nullable = false, updatable = false, length = 64) private String impressaoEnvio;
    protected Maromba() {}
    public Maromba(Unidade unidade, Formato formato, Usuario autor, LocalDate data, int quantidade, UUID chave, String impressao, Instant agora) {
        this.id = UUID.randomUUID(); this.unidade = unidade; this.formato = formato; this.autor = autor; this.data = data;
        this.quantidade = quantidade; this.situacao = Situacao.PENDENTE; this.origem = Origem.DIGITADO;
        this.criadoEm = agora; this.chaveEnvio = chave; this.impressaoEnvio = impressao;
    }
    public void editar(LocalDate data, int quantidade) { this.data = data; this.quantidade = quantidade; }
    public void validar(Usuario gestor, Instant agora) { situacao = Situacao.VALIDADO; validador = gestor; validadoEm = agora; }
    public String retrato() {
        return "Data: " + data + " · " + formato.getNome() + ": " + quantidade + " · " + situacao.getDescricao();
    }
    public UUID getId() { return id; }
    public Unidade getUnidade() { return unidade; }
    public Formato getFormato() { return formato; }
    public Usuario getAutor() { return autor; }
    public Usuario getValidador() { return validador; }
    public LocalDate getData() { return data; }
    public int getQuantidade() { return quantidade; }
    public Situacao getSituacao() { return situacao; }
    public Origem getOrigem() { return origem; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getValidadoEm() { return validadoEm; }
    public long getVersao() { return versao; }
    public UUID getChaveEnvio() { return chaveEnvio; }
    public String getImpressaoEnvio() { return impressaoEnvio; }
}
