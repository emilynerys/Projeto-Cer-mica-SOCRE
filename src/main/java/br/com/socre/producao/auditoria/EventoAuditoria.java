package br.com.socre.producao.auditoria;

import br.com.socre.producao.acesso.Usuario;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "auditoria")
public class EventoAuditoria {
    @Id private UUID id;
    @Column(name = "lancamento_id", nullable = false) private UUID lancamentoId;
    @ManyToOne @JoinColumn(name = "usuario_id", nullable = false) private Usuario usuario;
    @Column(nullable = false, length = 20) private String acao;
    @Column(name = "ocorrido_em", nullable = false) private Instant ocorridoEm;
    @Column(name = "valor_anterior", columnDefinition = "text") private String valorAnterior;
    @Column(name = "valor_novo", nullable = false, columnDefinition = "text") private String valorNovo;
    protected EventoAuditoria() {}
    public EventoAuditoria(UUID lancamentoId, Usuario usuario, String acao, String antes, String depois, Instant agora) {
        this.id = UUID.randomUUID(); this.lancamentoId = lancamentoId; this.usuario = usuario;
        this.acao = acao; this.valorAnterior = antes; this.valorNovo = depois; this.ocorridoEm = agora;
    }
    public String getAcao() { return switch(acao) { case "CRIACAO" -> "Registro criado"; case "EDICAO" -> "Registro corrigido"; default -> "Registro validado"; }; }
    public Usuario getUsuario() { return usuario; }
    public Instant getOcorridoEm() { return ocorridoEm; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNovo() { return valorNovo; }
}
