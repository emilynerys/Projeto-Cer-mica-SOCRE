package br.com.socre.producao.producao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

// RN20: the kiln content per furnace load and the finished product per quality at the reference moment.
public class InventarioForm {
    @NotNull(message = "Escolha a unidade.") private UUID idUnidade;
    @NotNull(message = "Informe a data e a hora de referência.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime referencia;
    private List<@Valid Item> itens = new ArrayList<>();
    public static class Item {
        @NotNull private InventarioInicial.Estado estado;
        @NotNull private UUID idFormato;
        /** Only for bricks in the kiln: the load as the factory calls it ("2ª de agosto/2026"). */
        private YearMonth mesFornada;
        private Integer numeroFornada;
        @Min(0) private Integer linhas;
        @NotNull @Min(0) private Integer quantidade;
        public Item() {}
        public Item(InventarioInicial.Estado estado, UUID idFormato, YearMonth mesFornada, Integer numeroFornada, Integer linhas, Integer quantidade) {
            this.estado = estado; this.idFormato = idFormato; this.mesFornada = mesFornada; this.numeroFornada = numeroFornada;
            this.linhas = linhas; this.quantidade = quantidade;
        }
        public InventarioInicial.Estado getEstado() { return estado; } public void setEstado(InventarioInicial.Estado v) { estado = v; }
        public UUID getIdFormato() { return idFormato; } public void setIdFormato(UUID v) { idFormato = v; }
        public YearMonth getMesFornada() { return mesFornada; } public void setMesFornada(YearMonth v) { mesFornada = v; }
        public Integer getNumeroFornada() { return numeroFornada; } public void setNumeroFornada(Integer v) { numeroFornada = v; }
        public Integer getLinhas() { return linhas; } public void setLinhas(Integer v) { linhas = v; }
        public Integer getQuantidade() { return quantidade; } public void setQuantidade(Integer v) { quantidade = v; }
    }
    public UUID getIdUnidade() { return idUnidade; } public void setIdUnidade(UUID v) { idUnidade = v; }
    public LocalDateTime getReferencia() { return referencia; } public void setReferencia(LocalDateTime v) { referencia = v; }
    public List<Item> getItens() { return itens; } public void setItens(List<Item> v) { itens = v; }
}
