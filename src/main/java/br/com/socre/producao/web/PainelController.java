package br.com.socre.producao.web;

import br.com.socre.producao.acesso.UsuarioAutenticado;
import br.com.socre.producao.cadastro.Formato;
import br.com.socre.producao.cadastro.FormatoRepository;
import br.com.socre.producao.cadastro.UnidadeRepository;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PainelController {
    private final MarombaRepository registros; private final UnidadeRepository unidades; private final ProducaoService service;
    private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas; private final FornadaService fornadas;
    private final FormatoRepository formatos; private final InventarioRepository inventarios; private final Clock clock; private final PanoramaService panorama;
    private final EstoqueService estoque;
    public PainelController(MarombaRepository registros, UnidadeRepository unidades, ProducaoService service, EnfornaRepository enfornas,
            DesenfornaRepository desenfornas, FornadaService fornadas, FormatoRepository formatos, InventarioRepository inventarios, Clock clock,
            PanoramaService panorama, EstoqueService estoque) {
        this.registros = registros; this.unidades = unidades; this.service = service; this.enfornas = enfornas; this.desenfornas = desenfornas;
        this.fornadas = fornadas; this.formatos = formatos; this.inventarios = inventarios; this.clock = clock; this.panorama = panorama; this.estoque = estoque;
    }
    /** What one load produced and how its quality compares with the load before it (in percentage points). */
    public record PorFornada(Balanco balanco, String unidade, Integer ocupacao, Double primeira, Double segunda, Double cru,
            Double variacaoPrimeira, Double variacaoCru) {
        public String setaPrimeira() { return seta(variacaoPrimeira); }
        public String setaCru() { return seta(variacaoCru); }
        /** "▲ 3 p.p." or "▼ 2 p.p."; null when there is nothing to compare. */
        private static String seta(Double variacao) {
            return variacao == null ? null : (variacao >= 0 ? "▲ " : "▼ ") + Math.round(Math.abs(variacao)) + " p.p.";
        }
    }
    private static final int FORNADAS_NO_PAINEL = 4;
    private static final Map<String, String> ETAPAS = etapas();
    private static Map<String, String> etapas() {
        var mapa = new LinkedHashMap<String, String>();
        mapa.put("MAROMBA", "Maromba"); mapa.put("ENFORNA", "Enforna"); mapa.put("DESENFORNA", "Desenforna");
        return mapa;
    }
    // One table row per day, one column per format shown.
    public record Dia(LocalDate data, Map<UUID, Long> porFormato, long total) {}
    /** Validated volume of each stage in the period, for one format or for all of them. */
    public record Etapas(String nome, long maromba, long linhasEnfornadas, long enforna, long linhasDesenfornadas, long primeira, long segunda, long cru) {
        public long acabado() { return primeira + segunda; }
        public long desenfornado() { return primeira + segunda + cru; }
        /** RN19: Maromba minus Enforna. It is a movement, not a stock, so it can be negative. */
        public long patio() { return maromba - enforna; }
        /** Share of a quality (1ª, 2ª or Cru) in what came out of the kiln; null when nothing came out. */
        public Integer percentual(long parte) { return desenfornado() == 0 ? null : (int) Math.round(parte * 100.0 / desenfornado()); }
        Etapas somar(Etapas o) {
            return new Etapas(nome, maromba + o.maromba, linhasEnfornadas + o.linhasEnfornadas, enforna + o.enforna,
                linhasDesenfornadas + o.linhasDesenfornadas, primeira + o.primeira, segunda + o.segunda, cru + o.cru);
        }
    }
    /** One day of the daily chart, in bricks; the stages are drawn side by side and never added (RN07). */
    public record Ponto(LocalDate data, long maromba, long enforna, long desenforna) {}
    /** A dashboard card: this period against the previous one of the same length. */
    public record Cartao(String nome, String valor, String detalhe, String tendencia, boolean melhorou) {}
    /** RF20: a ready-made period, as a link that keeps the other filters. */
    public record Atalho(String nome, LocalDate inicio, LocalDate fim) {}
    /** Everything the Indicadores screen and its PDF show for the same filters; diasSemRegistro is null across all units. */
    public record Indicadores(LocalDate inicio, LocalDate fim, UUID unidade, String etapa, UUID formato, List<Formato> formatos, List<Dia> diarios, Map<UUID, Long> totalPorFormato,
            long total, List<Etapas> etapas, Etapas etapasTotal, List<PorFornada> porFornada, List<LocalDate> diasSemRegistro, List<Ponto> serie) {
        public boolean verMaromba() { return etapa == null || etapa.equals("MAROMBA"); }
        public boolean verEnforna() { return etapa == null || etapa.equals("ENFORNA"); }
        public boolean verDesenforna() { return etapa == null || etapa.equals("DESENFORNA"); }
        /** The yard needs both Maromba and Enforna. */
        public boolean verPatio() { return etapa == null; }
        public boolean verFornadas() { return verEnforna() || verDesenforna(); }
    }
    @GetMapping("/painel") String painel(Model model, @AuthenticationPrincipal UsuarioAutenticado p,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID unidade, @RequestParam(required = false) String etapa, @RequestParam(required = false) UUID formato,
            @RequestParam(defaultValue = "false") boolean tv) {
        unidade = unidadeOuUnica(unidade);
        var d = indicadores(inicio, fim, unidade, etapa, formato, p);
        model.addAttribute("d", d);
        // The same length right before, for the arrows of the cards.
        long dias = java.time.temporal.ChronoUnit.DAYS.between(d.inicio(), d.fim()) + 1;
        var anterior = indicadores(d.inicio().minusDays(dias), d.inicio().minusDays(1), unidade, etapa, formato, p);
        model.addAttribute("cartoes", cartoes(d, anterior));
        model.addAttribute("graficos", Json.de(graficos(d, unidade, p)));
        model.addAttribute("tv", tv);
        model.addAttribute("diarios", d.diarios()); model.addAttribute("inicio", d.inicio()); model.addAttribute("fim", d.fim());
        model.addAttribute("formatos", d.formatos()); model.addAttribute("totalPorFormato", d.totalPorFormato());
        model.addAttribute("total", d.total()); model.addAttribute("etapas", d.etapas()); model.addAttribute("etapasTotal", d.etapasTotal());
        model.addAttribute("unidadeSelecionada", unidade); model.addAttribute("unidades", unidades.findAllByOrderByNome());
        model.addAttribute("porFornada", d.porFornada()); model.addAttribute("diasSemRegistro", d.diasSemRegistro());
        model.addAttribute("etapaSelecionada", d.etapa()); model.addAttribute("formatoSelecionado", formato);
        model.addAttribute("opcoesEtapa", ETAPAS); model.addAttribute("opcoesFormato", formatos.findAllByOrderByNome());
        model.addAttribute("atalhos", atalhos(unidade));
        model.addAttribute("panorama", panorama.de(p.unidadeId()));
        return "painel";
    }
    /** RF27: the screen as a file, with the same filters. */
    @GetMapping("/painel/relatorio.pdf") ResponseEntity<byte[]> relatorio(@AuthenticationPrincipal UsuarioAutenticado p,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID unidade, @RequestParam(required = false) String etapa, @RequestParam(required = false) UUID formato) {
        unidade = unidadeOuUnica(unidade);
        String nomeUnidade = unidade == null ? "Todas as unidades"
            : unidades.findById(unidade).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getNome();
        var d = indicadores(inicio, fim, unidade, etapa, formato, p);
        var filtros = new ArrayList<String>();
        if (d.etapa() != null) filtros.add("Etapa: " + ETAPAS.get(d.etapa()));
        if (formato != null) filtros.add("Formato: " + formatos.findById(formato).map(Formato::getNome).orElse("—"));
        var arquivo = ContentDisposition.attachment().filename("indicadores-" + d.inicio() + "-a-" + d.fim() + ".pdf").build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, arquivo.toString())
            .body(RelatorioPainelPdf.gerar(d, nomeUnidade, String.join("   ·   ", filtros), p.nome(), clock.instant()));
    }
    /** With a single unit there is nothing to choose, and the days without records can already be shown. */
    private UUID unidadeOuUnica(UUID unidade) {
        if (unidade != null) return unidade;
        var todas = unidades.findAllByOrderByNome();
        return todas.size() == 1 ? todas.getFirst().getId() : null;
    }
    private Indicadores indicadores(LocalDate inicio, LocalDate fim, UUID unidade, String etapa, UUID formato, UsuarioAutenticado p) {
        if (inicio == null) inicio = service.hoje().withDayOfMonth(1);
        if (fim == null) fim = service.hoje();
        ProducaoController.validarPeriodo(inicio, fim);
        // "Todas" arrives as an empty value.
        if (etapa != null && etapa.isBlank()) etapa = null;
        if (etapa != null && !ETAPAS.containsKey(etapa)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma etapa da lista.");
        var porDia = new TreeMap<LocalDate, Map<UUID, Long>>();
        var totalPorFormato = new HashMap<UUID, Long>();
        for (var t : registros.totais(inicio, fim, unidade)) {
            if (formato != null && !formato.equals(t.getFormato())) continue;
            porDia.computeIfAbsent(t.getData(), d -> new HashMap<>()).put(t.getFormato(), t.getQuantidade());
            totalPorFormato.merge(t.getFormato(), t.getQuantidade(), Long::sum);
        }
        var diarios = porDia.entrySet().stream()
            .map(e -> new Dia(e.getKey(), e.getValue(), e.getValue().values().stream().mapToLong(Long::longValue).sum())).toList();
        var etapas = new ArrayList<Etapas>();
        var mostrados = new ArrayList<Formato>();
        var enfornados = enfornas.consultar(inicio, fim, null, unidade, Situacao.VALIDADO);
        var desenfornados = desenfornas.consultar(inicio, fim, null, unidade, Situacao.VALIDADO);
        for (var f : formatos.findAllByOrderByNome()) {
            var id = f.getId();
            if (formato != null && !id.equals(formato)) continue;
            var en = enfornados.stream().filter(e -> e.getFormato().getId().equals(id)).toList();
            var de = desenfornados.stream().filter(d -> d.getFormato().getId().equals(id)).toList();
            // A format taken out of use leaves the forms, but what it produced still counts.
            if (!f.isAtivo() && !id.equals(formato) && en.isEmpty() && de.isEmpty() && !totalPorFormato.containsKey(id)) continue;
            mostrados.add(f);
            etapas.add(new Etapas(f.getNome(), totalPorFormato.getOrDefault(id, 0L),
                en.stream().mapToLong(Enforna::getLinhas).sum(), en.stream().mapToLong(Enforna::getQuantidade).sum(),
                de.stream().mapToLong(Desenforna::getLinhas).sum(), de.stream().mapToLong(Desenforna::getPrimeira).sum(),
                de.stream().mapToLong(Desenforna::getSegunda).sum(), de.stream().mapToLong(Desenforna::getCru).sum()));
        }
        var porFornada = new ArrayList<PorFornada>();
        for (var u : unidades.findAllByOrderByNome())
            if (unidade == null || u.getId().equals(unidade)) porFornada.addAll(ultimasFornadas(u.getId(), u.getNome(), EnfornaService.capacidade(u), formato, p));
        List<LocalDate> diasSemRegistro = null;
        if (unidade != null) {
            var presentes = new HashSet<>(registros.datasRegistradas(inicio, fim, unidade));
            var limite = fim.isBefore(service.hoje()) ? fim : service.hoje();
            diasSemRegistro = inicio.isAfter(limite) ? List.of() : inicio.datesUntil(limite.plusDays(1)).filter(d -> !presentes.contains(d)).toList();
        }
        var porData = new TreeMap<LocalDate, long[]>();
        for (var dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) porData.put(dia, new long[3]);
        diarios.forEach(x -> porData.get(x.data())[0] += x.total());
        enfornados.stream().filter(e -> formato == null || e.getFormato().getId().equals(formato)).forEach(e -> porData.get(e.getData())[1] += e.getQuantidade());
        desenfornados.stream().filter(x -> formato == null || x.getFormato().getId().equals(formato)).forEach(x -> porData.get(x.getData())[2] += x.getAcabado());
        var serie = porData.entrySet().stream().map(e -> new Ponto(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        return new Indicadores(inicio, fim, unidade, etapa, formato, mostrados, diarios, totalPorFormato,
            totalPorFormato.values().stream().mapToLong(Long::longValue).sum(), etapas,
            etapas.stream().reduce(new Etapas("Total", 0, 0, 0, 0, 0, 0, 0), Etapas::somar), porFornada, diasSemRegistro, serie);
    }
    private static final TemplateAdvice.Formatos FMT = new TemplateAdvice.Formatos();
    /** Maromba, Enforna, finished output and share of 1st quality, each against the previous period. */
    private static List<Cartao> cartoes(Indicadores d, Indicadores antes) {
        var t = d.etapasTotal(); var a = antes.etapasTotal();
        Integer q = t.percentual(t.primeira()), qa = a.percentual(a.primeira());
        return List.of(
            volume("Maromba", d.total(), antes.total()),
            volume("Enforna", t.enforna(), a.enforna()),
            volume("Desenforna (1ª + 2ª)", t.acabado(), a.acabado()),
            new Cartao("1ª qualidade", q == null ? "—" : q + "%", "do que saiu do forno",
                q == null || qa == null ? "sem comparação" : (q >= qa ? "▲ " : "▼ ") + Math.abs(q - qa) + " p.p. no período anterior", q != null && qa != null && q >= qa));
    }
    private static Cartao volume(String nome, long agora, long antes) {
        String tendencia = antes == 0 ? "sem comparação" : (agora >= antes ? "▲ " : "▼ ") + Math.round(Math.abs(agora - antes) * 100.0 / antes) + "% no período anterior";
        return new Cartao(nome, FMT.milheiros(agora), "milheiros", tendencia, antes != 0 && agora >= antes);
    }
    /** What the charts draw, in thousands of bricks, read by app.js. */
    private Map<String, Object> graficos(Indicadores d, UUID unidade, UsuarioAutenticado p) {
        var g = new LinkedHashMap<String, Object>();
        var rotulo = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        g.put("dias", d.serie().stream().map(x -> x.data().format(rotulo)).toList());
        g.put("maromba", d.serie().stream().map(x -> mil(x.maromba())).toList());
        g.put("enforna", d.serie().stream().map(x -> mil(x.enforna())).toList());
        g.put("desenforna", d.serie().stream().map(x -> mil(x.desenforna())).toList());
        // RN19 by week: Maromba against Enforna.
        var semanas = new TreeMap<LocalDate, long[]>();
        d.serie().forEach(x -> { var s = semanas.computeIfAbsent(x.data().with(DayOfWeek.MONDAY), k -> new long[2]); s[0] += x.maromba(); s[1] += x.enforna(); });
        g.put("semanas", semanas.keySet().stream().map(x -> "sem. " + x.format(rotulo)).toList());
        g.put("patioMaromba", semanas.values().stream().map(x -> mil(x[0])).toList());
        g.put("patioEnforna", semanas.values().stream().map(x -> mil(x[1])).toList());
        // Quality of the last loads, oldest first, from what was counted at the Desenforna.
        var fornadasComSaida = new ArrayList<>(d.porFornada().stream().filter(f -> f.primeira() != null).toList());
        Collections.reverse(fornadasComSaida);
        g.put("fornadas", fornadasComSaida.stream().map(f -> f.balanco().fornada().getNumero() + "ª " + f.balanco().fornada().getMes().getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag("pt-BR"))).toList());
        g.put("primeira", fornadasComSaida.stream().map(f -> umaCasa(f.primeira())).toList());
        g.put("segunda", fornadasComSaida.stream().map(f -> umaCasa(f.segunda())).toList());
        g.put("cru", fornadasComSaida.stream().map(f -> umaCasa(f.cru())).toList());
        // Gauges: quality of the period, occupation of the load being filled, speed of the last measured firing.
        var t = d.etapasTotal();
        g.put("qualidade", t.percentual(t.primeira()));
        g.put("qualidadeMedia", fornadasComSaida.isEmpty() ? null : umaCasa(fornadasComSaida.stream().mapToDouble(PorFornada::primeira).average().orElse(0)));
        g.put("ocupacao", d.porFornada().stream().filter(f -> f.balanco().fornada().isRecebendoEnforna()).map(PorFornada::ocupacao).filter(Objects::nonNull).findFirst().orElse(null));
        var velocidades = d.porFornada().stream().map(f -> f.balanco().linhasPor24h()).filter(Objects::nonNull).toList();
        g.put("velocidade", velocidades.isEmpty() ? null : umaCasa(velocidades.getFirst()));
        g.put("velocidadeMedia", velocidades.isEmpty() ? null : umaCasa(velocidades.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        // Burnt brick stock: yard count and Assado lines of the last map.
        var q = estoque.quadro(unidades.deTrabalho(unidade).getId());
        var rotulos = new ArrayList<String>(); var patio = new ArrayList<Double>(); var forno = new ArrayList<Double>();
        for (var l : q.linhas()) {
            rotulos.add(l.formato().getSigla() + " 1ª"); patio.add(mil(l.patioPrimeira() == null ? 0 : l.patioPrimeira())); forno.add(mil(l.forno()));
            rotulos.add(l.formato().getSigla() + " 2ª"); patio.add(mil(l.patioSegunda() == null ? 0 : l.patioSegunda())); forno.add(0.0);
        }
        g.put("estoqueRotulos", rotulos); g.put("estoquePatio", patio); g.put("estoqueForno", forno);
        return g;
    }
    private static double mil(long tijolos) { return Math.round(tijolos / 100.0) / 10.0; }
    private static Double umaCasa(Double v) { return v == null ? null : Math.round(v * 10) / 10.0; }
    /** Today, this week, this month and everything since the initial inventory (at most the one year the filter accepts). */
    private List<Atalho> atalhos(UUID unidade) {
        var hoje = service.hoje();
        var umAno = hoje.minusYears(1);
        var desde = unidades.findAllByOrderByNome().stream().filter(u -> unidade == null || u.getId().equals(unidade))
            .flatMap(u -> inventarios.referencia(u.getId()).stream()).map(r -> LocalDate.ofInstant(r, RegistroApoio.FUSO))
            .min(Comparator.naturalOrder()).orElse(hoje.withDayOfYear(1));
        return List.of(new Atalho("Hoje", hoje, hoje), new Atalho("Esta semana", hoje.with(DayOfWeek.MONDAY), hoje),
            new Atalho("Este mês", hoje.withDayOfMonth(1), hoje), new Atalho("Acumulado", desde.isBefore(umAno) ? umAno : desde, hoje));
    }
    /** The most recent loads of a unit, newest first, each compared with the one opened before it. */
    private List<PorFornada> ultimasFornadas(UUID unidade, String nome, int capacidade, UUID formato, UsuarioAutenticado p) {
        // One extra load is read only to compare the oldest one shown.
        var balancos = fornadas.recentes(unidade, FORNADAS_NO_PAINEL + 1).stream().map(f -> fornadas.balanco(f.getId(), p)).toList();
        var lista = new ArrayList<PorFornada>();
        for (int i = 0; i < Math.min(FORNADAS_NO_PAINEL, balancos.size()); i++) {
            // RF28: the occupation is of the whole load, even when one format is being looked at.
            Integer ocupacao = capacidade <= 0 ? null : (int) Math.round(balancos.get(i).linhasEnfornadas() * 100.0 / capacidade);
            var b = doFormato(balancos.get(i), formato);
            var anterior = i + 1 < balancos.size() ? doFormato(balancos.get(i + 1), formato) : null;
            Double primeira = b.percentual(b.primeira()), cru = b.percentual(b.cru());
            lista.add(new PorFornada(b, nome, ocupacao, primeira, b.percentual(b.segunda()), cru,
                variacao(primeira, anterior == null ? null : anterior.percentual(anterior.primeira())),
                variacao(cru, anterior == null ? null : anterior.percentual(anterior.cru()))));
        }
        return lista;
    }
    private static Balanco doFormato(Balanco b, UUID formato) {
        if (formato == null) return b;
        return new Balanco(b.fornada(), b.formatos().stream().filter(x -> x.formato().getId().equals(formato)).toList(), b.horasEsquente(), b.horasQueima(), b.queimaEstimada());
    }
    private static Double variacao(Double atual, Double anterior) { return atual == null || anterior == null ? null : atual - anterior; }
}
