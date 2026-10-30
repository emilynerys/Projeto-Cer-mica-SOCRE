package br.com.socre.producao.cadastro;

import jakarta.persistence.*;
import java.util.UUID;

// Configurable reasons for a correction (RN15) or a cancellation (RN16).
@Entity @Table(name = "motivo")
public class Motivo {
    public enum Tipo { CORRECAO, CANCELAMENTO }
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Tipo tipo;
    @Column(nullable = false, length = 120) private String descricao;
    @Column(name = "exige_texto", nullable = false) private boolean exigeTexto;
    @Column(nullable = false) private boolean ativo;
    protected Motivo() {}
    public UUID getId() { return id; }
    public Tipo getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public boolean isExigeTexto() { return exigeTexto; }
    public boolean isAtivo() { return ativo; }
}
