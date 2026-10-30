package br.com.socre.producao.producao;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

// Fields every stage form carries: date, submission key, version, correction reason and the RN14 confirmation.
public abstract class FormEtapa {
    @NotNull(message = "Informe a data da produção.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate data;
    @NotNull(message = "Abra novamente o formulário para gerar um identificador de envio.") private UUID chaveEnvio;
    @Min(0) private long versao;
    private UUID idMotivo;
    @Size(max = 500, message = "Descreva o motivo em até 500 caracteres.") private String textoMotivo;
    private boolean confirmarAvisos;
    public LocalDate getData() { return data; } public void setData(LocalDate v) { data = v; }
    public UUID getChaveEnvio() { return chaveEnvio; } public void setChaveEnvio(UUID v) { chaveEnvio = v; }
    public long getVersao() { return versao; } public void setVersao(long v) { versao = v; }
    public UUID getIdMotivo() { return idMotivo; } public void setIdMotivo(UUID v) { idMotivo = v; }
    public String getTextoMotivo() { return textoMotivo; } public void setTextoMotivo(String v) { textoMotivo = v; }
    public boolean isConfirmarAvisos() { return confirmarAvisos; } public void setConfirmarAvisos(boolean v) { confirmarAvisos = v; }
}
