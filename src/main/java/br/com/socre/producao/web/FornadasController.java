package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Furnace loads of a unit: what went in and out, firing times and the manager's conclusion.
@Controller @RequestMapping("/fornadas")
public class FornadasController {
    private final FornadaService fornadas; private final UnidadeRepository unidades; private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas;
    private final StatusFornoRepository mapas; private final ProducaoService producao; private final PanoramaService panorama;
    public FornadasController(FornadaService fornadas, UnidadeRepository unidades, EnfornaRepository enfornas, DesenfornaRepository desenfornas,
            StatusFornoRepository mapas, ProducaoService producao, PanoramaService panorama) {
        this.fornadas = fornadas; this.unidades = unidades; this.enfornas = enfornas; this.desenfornas = desenfornas;
        this.mapas = mapas; this.producao = producao; this.panorama = panorama;
    }

    @GetMapping String lista(@AuthenticationPrincipal UsuarioAutenticado p, @RequestParam(required = false) UUID unidade, Model model) {
        var todas = unidades.findAllByOrderByNome();
        // Registro de Produção always sees its own unit; the other profiles choose one.
        UUID escolhida = p.perfil() == Perfil.REGISTRO_PRODUCAO ? p.unidadeId()
            : unidade != null ? unidade : todas.isEmpty() ? null : todas.getFirst().getId();
        model.addAttribute("unidades", todas); model.addAttribute("unidadeSelecionada", escolhida);
        if (escolhida != null) {
            var emAndamento = fornadas.emAndamento(escolhida).stream().map(f -> fornadas.balanco(f.getId(), p)).toList();
            var concluidas = fornadas.recentes(escolhida, 12).stream().filter(f -> !f.isEmAndamento()).limit(6)
                .map(f -> fornadas.balanco(f.getId(), p)).toList();
            model.addAttribute("emAndamento", emAndamento); model.addAttribute("concluidas", concluidas);
            // Each load in progress lists its records (newest first) and the kiln maps saved while it was open.
            var lancamentos = new HashMap<UUID, List<RegistroEtapa>>(); var mapasDaFornada = new HashMap<UUID, List<StatusForno>>();
            for (var b : emAndamento) {
                var lista = new ArrayList<RegistroEtapa>(enfornas.daFornada(b.fornada().getId())); lista.addAll(desenfornas.daFornada(b.fornada().getId()));
                lista.sort(Comparator.comparing(RegistroEtapa::getData).reversed().thenComparing(RegistroEtapa::getEtapa).thenComparing(r -> r.getFormato().getNome()));
                lancamentos.put(b.fornada().getId(), lista);
                mapasDaFornada.put(b.fornada().getId(), mapas.daFornada(b.fornada().getId(), org.springframework.data.domain.PageRequest.of(0, 10)));
            }
            model.addAttribute("lancamentos", lancamentos); model.addAttribute("mapasDaFornada", mapasDaFornada);
        }
        // RN08: the Gestor de Produção opens, closes and marks the firing times; the other profiles only read.
        boolean gestor = p.perfil() == Perfil.GESTOR_PRODUCAO;
        model.addAttribute("podeRegistrarMarco", gestor); model.addAttribute("podeConcluir", gestor); model.addAttribute("podeCorrigir", gestor);
        model.addAttribute("motivosCorrecao", producao.motivos(Motivo.Tipo.CORRECAO));
        model.addAttribute("panorama", panorama.de(p.perfil() == Perfil.REGISTRO_PRODUCAO ? p.unidadeId() : escolhida));
        model.addAttribute("podeAbrir", gestor && escolhida != null && fornadas.podeAbrirNova(escolhida));
        model.addAttribute("marcos", Fornada.Marco.manuais());
        model.addAttribute("agora", LocalDateTime.now(RegistroApoio.FUSO).withSecond(0).withNano(0));
        return "fornadas";
    }
    @PostMapping("/abrir") String abrir(@RequestParam UUID unidade, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        try {
            var f = fornadas.abrirNova(unidade, p);
            flash.addFlashAttribute("sucesso", f.getIdentificacao() + " aberta. A partir de agora, as Enfornas entram nela.");
        } catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        return "redirect:/fornadas?unidade=" + unidade;
    }
    // RN21 firing times; an empty time means now.
    @PostMapping("/{id}/marco") String marco(@PathVariable UUID id, @RequestParam long versao, @RequestParam Fornada.Marco marco,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime quando,
            @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        var f = fornadas.consultar(id, p);
        var instante = quando == null ? Instant.now() : quando.atZone(RegistroApoio.FUSO).toInstant();
        try { fornadas.registrarMarco(id, p, versao, marco, instante); flash.addFlashAttribute("sucesso", "Horário registrado na " + f.getIdentificacao() + "."); }
        catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        catch (OptimisticLockingFailureException e) { flash.addFlashAttribute("falha", "A fornada mudou desde que você abriu a tela. Confira e tente de novo."); }
        return "redirect:/fornadas?unidade=" + f.getUnidade().getId();
    }
    @PostMapping("/{id}/concluir") String concluir(@PathVariable UUID id, @RequestParam long versao, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        var f = fornadas.consultar(id, p);
        try {
            var b = fornadas.concluir(id, p, versao);
            flash.addFlashAttribute("sucesso", f.getIdentificacao() + " fechada. Diferença entre desenfornado e enfornado: "
                + new TemplateAdvice.Formatos().inteiro(b.diferenca()) + " tijolos.");
        } catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        catch (OptimisticLockingFailureException e) { flash.addFlashAttribute("falha", "A fornada mudou desde que você abriu a tela. Confira e tente de novo."); }
        return "redirect:/fornadas?unidade=" + f.getUnidade().getId();
    }
}
