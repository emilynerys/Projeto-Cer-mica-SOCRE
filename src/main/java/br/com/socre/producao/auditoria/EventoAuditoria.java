package br.com.socre.producao.auditoria;

import br.com.socre.producao.acesso.Usuario;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// The trail points to several tables (maromba, enforna, desenforna...), so it names the table.
@Entity @Table(name = "auditoria")
public class EventoAuditoria {
    @Id private UUID id;
    @Column(nullable = false, length = 30) private String tabela;
    @Column(name = "id_registro") private UUID idRegistro;
    @ManyToOne @JoinColumn(name = "id_usuario", nullable = false) private Usuario usuario;
    @Column(nullable = false, length = 20) private String acao;
    @Column(name = "data_hora", nullable = false) private Instant dataHora;
    @Column(columnDefinition = "text") private String antes;
    @Column(columnDefinition = "text") private String depois;
    protected EventoAuditoria() {}
    public EventoAuditoria(String tabela, UUID idRegistro, Usuario usuario, String acao, String antes, String depois, Instant agora) {
        this.id = UUID.randomUUID(); this.tabela = tabela; this.idRegistro = idRegistro; this.usuario = usuario;
        this.acao = acao; this.antes = antes; this.depois = depois; this.dataHora = agora;
    }
    public String getAcao() {
        return switch (acao) {
            case "CRIACAO" -> "Registro criado";
            case "EDICAO", "CORRECAO" -> "Registro corrigido";
            case "CANCELAMENTO" -> "Registro cancelado";
            case "IMPORTACAO" -> "Registro importado";
            default -> "Registro validado";
        };
    }
    public Usuario getUsuario() { return usuario; }
    public Instant getDataHora() { return dataHora; }
    public String getAntes() { return antes; }
    public String getDepois() { return depois; }
}
