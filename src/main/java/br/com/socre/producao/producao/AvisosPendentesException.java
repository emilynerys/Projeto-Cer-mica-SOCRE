package br.com.socre.producao.producao;

import java.util.List;

// Values outside the expected range: nothing is saved until the user confirms with one tap (RN14).
public class AvisosPendentesException extends RuntimeException {
    private final List<String> avisos;
    public AvisosPendentesException(List<String> avisos) { super(String.join(" ", avisos)); this.avisos = List.copyOf(avisos); }
    public List<String> getAvisos() { return avisos; }
}
