package br.com.socre.producao.producao;

import jakarta.validation.constraints.*;
import java.util.*;

// Creating shows every active format; correcting shows only the format of the record.
public class MarombaForm extends FormEtapa {
    private Map<UUID, @NotNull(message = "Informe todos os formatos, mesmo quando for zero.")
        @Min(value = 0, message = "Use uma quantidade maior ou igual a zero.")
        @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.") Integer> quantidades = new LinkedHashMap<>();
    public Map<UUID, Integer> getQuantidades() { return quantidades; } public void setQuantidades(Map<UUID, Integer> v) { quantidades = v; }
}
