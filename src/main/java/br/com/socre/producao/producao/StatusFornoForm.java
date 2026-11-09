package br.com.socre.producao.producao;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

// The "Status do Forno" paper: date and time, a note and, for each line, the brick format and its state.
public class StatusFornoForm {
    private UUID idUnidade;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime dataHora;
    private String observacao;
    /** Keyed by line number; a line without format and state is empty ("V"). */
    private Map<Integer, Linha> linhas = new TreeMap<>();
    public static class Linha {
        private UUID idFormato;
        private StatusForno.Estado estado;
        private Integer quantidade;
        public Linha() {}
        public Linha(UUID idFormato, StatusForno.Estado estado, Integer quantidade) { this.idFormato = idFormato; this.estado = estado; this.quantidade = quantidade; }
        public Integer getQuantidade() { return quantidade; } public void setQuantidade(Integer v) { quantidade = v; }
        public UUID getIdFormato() { return idFormato; } public void setIdFormato(UUID v) { idFormato = v; }
        public StatusForno.Estado getEstado() { return estado; } public void setEstado(StatusForno.Estado v) { estado = v; }
    }
    public UUID getIdUnidade() { return idUnidade; } public void setIdUnidade(UUID v) { idUnidade = v; }
    public LocalDateTime getDataHora() { return dataHora; } public void setDataHora(LocalDateTime v) { dataHora = v; }
    public String getObservacao() { return observacao; } public void setObservacao(String v) { observacao = v; }
    public Map<Integer, Linha> getLinhas() { return linhas; } public void setLinhas(Map<Integer, Linha> v) { linhas = v; }
}
