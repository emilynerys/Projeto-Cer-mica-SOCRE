package br.com.socre.producao.web;

import br.com.socre.producao.acesso.UsuarioAutenticado;
import br.com.socre.producao.cadastro.UnidadeRepository;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PainelController {
    private final MarombaRepository registros; private final UnidadeRepository unidades; private final ProducaoService service;
    private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas; private final FornadaService fornadas;
    public PainelController(MarombaRepository registros, UnidadeRepository unidades, ProducaoService service, EnfornaRepository enfornas,
            DesenfornaRepository desenfornas, FornadaService fornadas) {
        this.registros = registros; this.unidades = unidades; this.service = service; this.enfornas = enfornas; this.desenfornas = desenfornas;
        this.fornadas = fornadas;
    }
    /** What one load produced and how its quality compares with the load before it (in percentage points). */
    public record PorFornada(Balanco balanco, String unidade, Double primeira, Double segunda, Double cru,
            Double variacaoPrimeira, Double variacaoCru) {
        public String setaPrimeira() { return seta(variacaoPrimeira); }
        public String setaCru() { return seta(variacaoCru); }
        /** "▲ 3 p.p." or "▼ 2 p.p."; null when there is nothing to compare. */
        private static String seta(Double variacao) {
            return variacao == null ? null : (variacao >= 0 ? "▲ " : "▼ ") + Math.round(Math.abs(variacao)) + " p.p.";
        }
    }
    private static final int FORNADAS_NO_PAINEL = 4;
    // One table row per day, one column per active format.
    public record Dia(LocalDate data, Map<UUID, Long> porFormato, long total) {}
    /** Validated volume of each stage in the period, for one format or for all of them. */
    public record Etapas(String nome, long maromba, long linhasEnfornadas, long enforna, long linhasDesenfornadas, long primeira, long segunda, long cru) {
        public long acabado() { return primeira + segunda; }
        public long desenfornado() { return primeira + segunda + cru; }
        /** Share of 1st quality in what came out of the kiln; null when nothing came out. */
        public Integer percentualPrimeira() { return desenfornado() == 0 ? null : (int) Math.round(primeira * 100.0 / desenfornado()); }
        Etapas somar(Etapas o) {
            return new Etapas(nome, maromba + o.maromba, linhasEnfornadas + o.linhasEnfornadas, enforna + o.enforna,
                linhasDesenfornadas + o.linhasDesenfornadas, primeira + o.primeira, segunda + o.segunda, cru + o.cru);
        }
    }
    @GetMapping("/painel") String painel(Model model, @AuthenticationPrincipal UsuarioAutenticado p,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID unidade) {
        if (inicio == null) inicio = service.hoje().withDayOfMonth(1);
        if (fim == null) fim = service.hoje();
        ProducaoController.validarPeriodo(inicio, fim);
        var porDia = new TreeMap<LocalDate, Map<UUID, Long>>();
        var totalPorFormato = new HashMap<UUID, Long>();
        for (var t : registros.totais(inicio, fim, unidade)) {
            porDia.computeIfAbsent(t.getData(), d -> new HashMap<>()).put(t.getFormato(), t.getQuantidade());
            totalPorFormato.merge(t.getFormato(), t.getQuantidade(), Long::sum);
        }
        var diarios = porDia.entrySet().stream()
            .map(e -> new Dia(e.getKey(), e.getValue(), e.getValue().values().stream().mapToLong(Long::longValue).sum())).toList();
        model.addAttribute("diarios", diarios); model.addAttribute("inicio", inicio); model.addAttribute("fim", fim);
        model.addAttribute("formatos", service.formatosAtivos()); model.addAttribute("totalPorFormato", totalPorFormato);
        model.addAttribute("total", totalPorFormato.values().stream().mapToLong(Long::longValue).sum());
        var etapas = new ArrayList<Etapas>();
        var enfornados = enfornas.consultar(inicio, fim, null, unidade, Situacao.VALIDADO);
        var desenfornados = desenfornas.consultar(inicio, fim, null, unidade, Situacao.VALIDADO);
        for (var formato : service.formatosAtivos()) {
            var id = formato.getId();
            var en = enfornados.stream().filter(e -> e.getFormato().getId().equals(id)).toList();
            var de = desenfornados.stream().filter(d -> d.getFormato().getId().equals(id)).toList();
            etapas.add(new Etapas(formato.getNome(), totalPorFormato.getOrDefault(id, 0L),
                en.stream().mapToLong(Enforna::getLinhas).sum(), en.stream().mapToLong(Enforna::getQuantidade).sum(),
                de.stream().mapToLong(Desenforna::getLinhas).sum(), de.stream().mapToLong(Desenforna::getPrimeira).sum(),
                de.stream().mapToLong(Desenforna::getSegunda).sum(), de.stream().mapToLong(Desenforna::getCru).sum()));
        }
        model.addAttribute("etapas", etapas);
        model.addAttribute("etapasTotal", etapas.stream().reduce(new Etapas("Total", 0, 0, 0, 0, 0, 0, 0), Etapas::somar));
        model.addAttribute("unidadeSelecionada", unidade); model.addAttribute("unidades", unidades.findAllByOrderByNome());
        var porFornada = new ArrayList<PorFornada>();
        for (var u : unidades.findAllByOrderByNome())
            if (unidade == null || u.getId().equals(unidade)) porFornada.addAll(ultimasFornadas(u.getId(), u.getNome(), p));
        model.addAttribute("porFornada", porFornada);
        if (unidade != null) {
            var presentes = new HashSet<>(registros.datasRegistradas(inicio, fim, unidade));
            var limite = fim.isBefore(service.hoje()) ? fim : service.hoje();
            model.addAttribute("diasSemRegistro", inicio.isAfter(limite) ? List.of() : inicio.datesUntil(limite.plusDays(1)).filter(d -> !presentes.contains(d)).toList());
        }
        return "painel";
    }
    /** The most recent loads of a unit, newest first, each compared with the one opened before it. */
    private List<PorFornada> ultimasFornadas(UUID unidade, String nome, UsuarioAutenticado p) {
        // One extra load is read only to compare the oldest one shown.
        var balancos = fornadas.recentes(unidade, FORNADAS_NO_PAINEL + 1).stream().map(f -> fornadas.balanco(f.getId(), p)).toList();
        var lista = new ArrayList<PorFornada>();
        for (int i = 0; i < Math.min(FORNADAS_NO_PAINEL, balancos.size()); i++) {
            var b = balancos.get(i);
            var anterior = i + 1 < balancos.size() ? balancos.get(i + 1) : null;
            Double primeira = b.percentual(b.primeira()), cru = b.percentual(b.cru());
            lista.add(new PorFornada(b, nome, primeira, b.percentual(b.segunda()), cru,
                variacao(primeira, anterior == null ? null : anterior.percentual(anterior.primeira())),
                variacao(cru, anterior == null ? null : anterior.percentual(anterior.cru()))));
        }
        return lista;
    }
    private static Double variacao(Double atual, Double anterior) { return atual == null || anterior == null ? null : atual - anterior; }
}
