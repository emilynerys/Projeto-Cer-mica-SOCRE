package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.Usuario;
import br.com.socre.producao.cadastro.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

// Columns shared by the maromba, enforna and desenforna tables; each record is one format on one day.
@MappedSuperclass
public abstract class RegistroEtapa {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "id_formato", nullable = false) private Formato formato;
    @ManyToOne @JoinColumn(name = "id_autor", nullable = false) private Usuario autor;
    @ManyToOne @JoinColumn(name = "id_validador") private Usuario validador;
    @Column(nullable = false) private LocalDate data;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Situacao situacao;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Origem origem;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "validado_em") private Instant validadoEm;
    @Version private long versao;
    @Column(name = "chave_envio", nullable = false, updatable = false) private UUID chaveEnvio;
    @Column(name = "impressao_envio", nullable = false, updatable = false, length = 64) private String impressaoEnvio;
    protected RegistroEtapa() {}
    protected RegistroEtapa(Formato formato, Usuario autor, LocalDate data, UUID chave, String impressao, Instant agora) {
        this.id = UUID.randomUUID(); this.formato = formato; this.autor = autor; this.data = data;
        this.situacao = Situacao.PENDENTE; this.origem = Origem.DIGITADO;
        this.criadoEm = agora; this.chaveEnvio = chave; this.impressaoEnvio = impressao;
    }
    public abstract String getEtapa();
    public abstract String getTabela();
    public abstract Unidade getUnidade();
    protected abstract String valores();
    public String retrato() { return "Data: " + data + " · " + valores() + " · " + situacao.getDescricao(); }
    protected void mudarData(LocalDate data) { this.data = data; }
    public void validar(Usuario gestor, Instant agora) { situacao = Situacao.VALIDADO; validador = gestor; validadoEm = agora; }
    public void cancelar() { situacao = Situacao.CANCELADO; }
    public UUID getId() { return id; }
    public Formato getFormato() { return formato; }
    public Usuario getAutor() { return autor; }
    public Usuario getValidador() { return validador; }
    public LocalDate getData() { return data; }
    public Situacao getSituacao() { return situacao; }
    public Origem getOrigem() { return origem; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getValidadoEm() { return validadoEm; }
    public long getVersao() { return versao; }
    public UUID getChaveEnvio() { return chaveEnvio; }
    public String getImpressaoEnvio() { return impressaoEnvio; }
}
