package br.com.socre.producao.web;

import br.com.socre.producao.cadastro.UnidadeRepository;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PainelController {
    private final MarombaRepository registros; private final UnidadeRepository unidades; private final ProducaoService service;
    public PainelController(MarombaRepository registros, UnidadeRepository unidades, ProducaoService service) { this.registros = registros; this.unidades = unidades; this.service = service; }
    // One table row per day, one column per active format.
    public record Dia(LocalDate data, Map<UUID, Long> porFormato, long total) {}
    @GetMapping("/painel") String painel(Model model,
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
        model.addAttribute("unidadeSelecionada", unidade); model.addAttribute("unidades", unidades.findAllByOrderByNome());
        if (unidade != null) {
            var presentes = new HashSet<>(registros.datasRegistradas(inicio, fim, unidade));
            var limite = fim.isBefore(service.hoje()) ? fim : service.hoje();
            model.addAttribute("diasSemRegistro", inicio.isAfter(limite) ? List.of() : inicio.datesUntil(limite.plusDays(1)).filter(d -> !presentes.contains(d)).toList());
        }
        return "painel";
    }
}
