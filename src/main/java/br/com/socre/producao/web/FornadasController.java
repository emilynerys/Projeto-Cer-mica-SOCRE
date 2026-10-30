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
    private final FornadaService fornadas; private final UnidadeRepository unidades;
    public FornadasController(FornadaService fornadas, UnidadeRepository unidades) { this.fornadas = fornadas; this.unidades = unidades; }

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
        }
        model.addAttribute("podeRegistrarMarco", p.perfil() == Perfil.REGISTRO_PRODUCAO || p.perfil() == Perfil.GESTOR_PRODUCAO);
        model.addAttribute("podeConcluir", p.perfil() == Perfil.GESTOR_PRODUCAO);
        model.addAttribute("marcos", Fornada.Marco.values());
        model.addAttribute("agora", LocalDateTime.now(RegistroApoio.FUSO).withSecond(0).withNano(0));
        return "fornadas";
    }
    // RN08 "Enforna concluída" and RN21 firing times; an empty time means now.
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
            flash.addFlashAttribute("sucesso", f.getIdentificacao() + " concluída. Diferença entre desenfornado e enfornado: "
                + new TemplateAdvice.Formatos().inteiro(b.diferenca()) + " tijolos.");
        } catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        catch (OptimisticLockingFailureException e) { flash.addFlashAttribute("falha", "A fornada mudou desde que você abriu a tela. Confira e tente de novo."); }
        return "redirect:/fornadas?unidade=" + f.getUnidade().getId();
    }
}
