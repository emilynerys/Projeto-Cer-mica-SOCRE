package br.com.socre.producao.producao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

// Lines taken out and the 1st, 2nd and Cru bricks for each active format (RN10).
public class DesenfornaForm extends FormEtapa {
    private UUID idFornada;
    private Map<UUID, @Valid Item> itens = new LinkedHashMap<>();
    public static class Item {
        @Min(value = 0, message = "Use um número de linhas maior ou igual a zero.") @Max(value = 1000, message = "Confira o número de linhas.")
        private Integer linhas;
        @Min(value = 0, message = "Use quantidades maiores ou iguais a zero.") @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.")
        private Integer primeira;
        @Min(value = 0, message = "Use quantidades maiores ou iguais a zero.") @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.")
        private Integer segunda;
        @Min(value = 0, message = "Use quantidades maiores ou iguais a zero.") @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.")
        private Integer cru;
        public Item() {}
        public Item(Integer linhas, Integer primeira, Integer segunda, Integer cru) { this.linhas = linhas; this.primeira = primeira; this.segunda = segunda; this.cru = cru; }
        public Integer getLinhas() { return linhas; } public void setLinhas(Integer v) { linhas = v; }
        public Integer getPrimeira() { return primeira; } public void setPrimeira(Integer v) { primeira = v; }
        public Integer getSegunda() { return segunda; } public void setSegunda(Integer v) { segunda = v; }
        public Integer getCru() { return cru; } public void setCru(Integer v) { cru = v; }
    }
    public UUID getIdFornada() { return idFornada; } public void setIdFornada(UUID v) { idFornada = v; }
    public Map<UUID, Item> getItens() { return itens; } public void setItens(Map<UUID, Item> v) { itens = v; }
}
