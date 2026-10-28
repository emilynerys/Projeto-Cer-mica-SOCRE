package br.com.socre.producao.producao;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public class MarombaForm {
    @NotNull(message = "Informe a data da produção.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate data;
    @NotNull(message = "Informe 6 furos, mesmo quando for zero.") @Min(value = 0, message = "Use uma quantidade maior ou igual a zero.")
    @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.") private Integer quantidade6f = 0;
    @NotNull(message = "Informe 8 furos, mesmo quando for zero.") @Min(value = 0, message = "Use uma quantidade maior ou igual a zero.")
    @Max(value = 1000000000, message = "Quantidade muito alta. Confira o valor.") private Integer quantidade8f = 0;
    @NotNull(message = "Abra novamente o formulário para gerar um identificador de envio.") private UUID chaveEnvio;
    @Min(0) private long versao;
    public LocalDate getData() { return data; } public void setData(LocalDate v) { data = v; }
    public Integer getQuantidade6f() { return quantidade6f; } public void setQuantidade6f(Integer v) { quantidade6f = v; }
    public Integer getQuantidade8f() { return quantidade8f; } public void setQuantidade8f(Integer v) { quantidade8f = v; }
    public UUID getChaveEnvio() { return chaveEnvio; } public void setChaveEnvio(UUID v) { chaveEnvio = v; }
    public long getVersao() { return versao; } public void setVersao(long v) { versao = v; }
}
