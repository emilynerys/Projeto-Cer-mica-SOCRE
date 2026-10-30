package br.com.socre.producao.producao;

import br.com.socre.producao.cadastro.Formato;
import java.util.List;

// What went into and came out of a furnace load (RN08) and how long the fire took (RN21).
public record Balanco(Fornada fornada, List<PorFormato> formatos, Double horasEsquente, Double horasQueima) {
    public record PorFormato(Formato formato, int linhasEnfornadas, long tijolosEnfornados, int linhasDesenfornadas,
            long tijolosDesenfornados, long acabado, long cru) {
        /** Positive: more came out than went in; negative: less (estimates per line, breakage, Cru left in the kiln). */
        public long diferenca() { return tijolosDesenfornados - tijolosEnfornados; }
    }
    public int linhasEnfornadas() { return formatos.stream().mapToInt(PorFormato::linhasEnfornadas).sum(); }
    public int linhasDesenfornadas() { return formatos.stream().mapToInt(PorFormato::linhasDesenfornadas).sum(); }
    public long tijolosEnfornados() { return formatos.stream().mapToLong(PorFormato::tijolosEnfornados).sum(); }
    public long tijolosDesenfornados() { return formatos.stream().mapToLong(PorFormato::tijolosDesenfornados).sum(); }
    public long diferenca() { return tijolosDesenfornados() - tijolosEnfornados(); }
    /** RN08: suggest the conclusion once the lines taken out reach the lines put in; never conclude automatically. */
    public boolean sugerirConclusao() { return linhasEnfornadas() > 0 && linhasDesenfornadas() >= linhasEnfornadas(); }
    /** RN21: average firing speed in lines per 24 hours. */
    public Double linhasPor24h() {
        return horasQueima == null || horasQueima <= 0 ? null : linhasEnfornadas() * 24 / horasQueima;
    }
    public String resumo() {
        return "Enfornado " + linhasEnfornadas() + " linhas / " + tijolosEnfornados() + " tijolos · Desenfornado "
            + linhasDesenfornadas() + " linhas / " + tijolosDesenfornados() + " tijolos · Diferença " + diferenca();
    }

    static final class Acumulado {
        private final Formato formato; private int linhasEntrou, linhasSaiu; private long entrou, saiu, acabado, cru;
        Acumulado(Formato formato) { this.formato = formato; }
        void entrou(int linhas, long tijolos) { linhasEntrou += linhas; entrou += tijolos; }
        void saiu(Desenforna d) { linhasSaiu += d.getLinhas(); saiu += d.getTotal(); acabado += d.getAcabado(); cru += d.getCru(); }
        PorFormato fechar() { return new PorFormato(formato, linhasEntrou, entrou, linhasSaiu, saiu, acabado, cru); }
    }
}
