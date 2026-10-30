package br.com.socre.producao.producao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

// Lines and bricks per line for each active format; the total is suggested and can be changed (RN12).
public class EnfornaForm extends FormEtapa {
    private UUID idFornada;
    private boolean novaFornada;
    private Map<UUID, @Valid Item> itens = new LinkedHashMap<>();
    public static class Item {
        @Min(value = 0, message = "Use um número de linhas maior ou igual a zero.") @Max(value = 1000, message = "Confira o número de linhas.")
        private Integer linhas;
        @Min(value = 1, message = "Use ao menos 1 tijolo por linha.") @Max(value = 100000, message = "Confira os tijolos por linha.")
        private Integer tijolosPorLinha;
        @Min(value = 0, message = "Use uma quantidade maior ou igual a zero.") @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.")
        private Integer quantidade;
        public Item() {}
        public Item(Integer linhas, Integer tijolosPorLinha, Integer quantidade) { this.linhas = linhas; this.tijolosPorLinha = tijolosPorLinha; this.quantidade = quantidade; }
        public Integer getLinhas() { return linhas; } public void setLinhas(Integer v) { linhas = v; }
        public Integer getTijolosPorLinha() { return tijolosPorLinha; } public void setTijolosPorLinha(Integer v) { tijolosPorLinha = v; }
        public Integer getQuantidade() { return quantidade; } public void setQuantidade(Integer v) { quantidade = v; }
    }
    public UUID getIdFornada() { return idFornada; } public void setIdFornada(UUID v) { idFornada = v; }
    public boolean isNovaFornada() { return novaFornada; } public void setNovaFornada(boolean v) { novaFornada = v; }
    public Map<UUID, Item> getItens() { return itens; } public void setItens(Map<UUID, Item> v) { itens = v; }
}
