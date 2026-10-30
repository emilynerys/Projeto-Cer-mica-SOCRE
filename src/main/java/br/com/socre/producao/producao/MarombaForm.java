package br.com.socre.producao.producao;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

// Creating shows every active format; correcting shows only the format of the record.
public class MarombaForm {
    @NotNull(message = "Informe a data da produção.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate data;
    private Map<UUID, @NotNull(message = "Informe todos os formatos, mesmo quando for zero.")
        @Min(value = 0, message = "Use uma quantidade maior ou igual a zero.")
        @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.") Integer> quantidades = new LinkedHashMap<>();
    @NotNull(message = "Abra novamente o formulário para gerar um identificador de envio.") private UUID chaveEnvio;
    @Min(0) private long versao;
    public LocalDate getData() { return data; } public void setData(LocalDate v) { data = v; }
    public Map<UUID, Integer> getQuantidades() { return quantidades; } public void setQuantidades(Map<UUID, Integer> v) { quantidades = v; }
    public UUID getChaveEnvio() { return chaveEnvio; } public void setChaveEnvio(UUID v) { chaveEnvio = v; }
    public long getVersao() { return versao; } public void setVersao(long v) { versao = v; }
}
