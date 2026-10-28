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
    private final LancamentoRepository registros; private final UnidadeRepository unidades; private final ProducaoService service;
    public PainelController(LancamentoRepository registros, UnidadeRepository unidades, ProducaoService service) { this.registros = registros; this.unidades = unidades; this.service = service; }
    @GetMapping("/painel") String painel(Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) UUID unidade) {
        if (inicio == null) inicio = service.hoje().withDayOfMonth(1);
        if (fim == null) fim = service.hoje();
        ProducaoController.validarPeriodo(inicio, fim);
        var diarios = registros.totais(inicio, fim, unidade);
        model.addAttribute("diarios", diarios); model.addAttribute("inicio", inicio); model.addAttribute("fim", fim);
        model.addAttribute("unidadeSelecionada", unidade); model.addAttribute("unidades", unidades.findAllByOrderByNome());
        model.addAttribute("total6f", diarios.stream().mapToLong(LancamentoRepository.TotalDiario::getQuantidade6f).sum());
        model.addAttribute("total8f", diarios.stream().mapToLong(LancamentoRepository.TotalDiario::getQuantidade8f).sum());
        if (unidade != null) {
            var presentes = new HashSet<>(registros.datasRegistradas(inicio, fim, unidade));
            var limite = fim.isBefore(service.hoje()) ? fim : service.hoje();
            model.addAttribute("diasSemRegistro", inicio.isAfter(limite) ? List.of() : inicio.datesUntil(limite.plusDays(1)).filter(d -> !presentes.contains(d)).toList());
        }
        return "painel";
    }
}
