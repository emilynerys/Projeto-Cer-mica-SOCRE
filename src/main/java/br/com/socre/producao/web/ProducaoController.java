package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.UnidadeRepository;
import br.com.socre.producao.producao.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/producao")
public class ProducaoController {
    private final ProducaoService service; private final MarombaRepository marombas; private final UnidadeRepository unidades;
    public ProducaoController(ProducaoService service, MarombaRepository marombas, UnidadeRepository unidades) {
        this.service = service; this.marombas = marombas; this.unidades = unidades;
    }
    @GetMapping String lista(@AuthenticationPrincipal UsuarioAutenticado p, Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Situacao situacao, @RequestParam(defaultValue = "0") int pagina) {
        if (inicio == null) inicio = service.hoje().minusDays(14);
        if (fim == null) fim = service.hoje();
        validarPeriodo(inicio, fim);
        boolean registroProducao = p.perfil() == Perfil.REGISTRO_PRODUCAO;
        var resultado = marombas.consultar(inicio, fim, registroProducao ? p.id() : null, registroProducao ? p.unidadeId() : null, situacao,
            PageRequest.of(Math.max(0, pagina), 30, Sort.by(Sort.Order.asc("situacao"), Sort.Order.desc("data"), Sort.Order.desc("criadoEm"), Sort.Order.asc("formato.nome"))));
        model.addAttribute("registros", resultado); model.addAttribute("inicio", inicio); model.addAttribute("fim", fim);
        model.addAttribute("situacao", situacao); model.addAttribute("registroProducao", registroProducao);
        return "producao/lista";
    }
    @GetMapping("/maromba") String nova(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var f = new MarombaForm(); f.setData(service.hoje()); f.setChaveEnvio(UUID.randomUUID());
        service.formatosAtivos().forEach(formato -> f.getQuantidades().put(formato.getId(), 0));
        model.addAttribute("form", f); prepararForm(model, p, null); return "producao/form";
    }
    @GetMapping("/{id}") String detalhe(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var m = service.consultar(id, p); model.addAttribute("registro", m);
        model.addAttribute("historico", service.historico(id));
        model.addAttribute("podeEditar", m.getSituacao() == Situacao.PENDENTE && (p.perfil() == Perfil.REGISTRO_PRODUCAO || p.perfil() == Perfil.GESTOR_PRODUCAO));
        model.addAttribute("podeValidar", m.getSituacao() == Situacao.PENDENTE && p.perfil() == Perfil.GESTOR_PRODUCAO);
        return "producao/detalhe";
    }
    @GetMapping("/{id}/editar") String editar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var m = service.consultar(id, p); service.exigirEdicao(m, p);
        var f = new MarombaForm(); f.setData(m.getData()); f.getQuantidades().put(m.getFormato().getId(), m.getQuantidade());
        f.setVersao(m.getVersao()); f.setChaveEnvio(m.getChaveEnvio());
        model.addAttribute("form", f); prepararForm(model, p, m); return "producao/form";
    }
    @PostMapping("/maromba") Object criar(@Valid @ModelAttribute("form") MarombaForm form, BindingResult errors,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model, HttpServletRequest req) {
        return salvar(null, form, errors, p, model, req);
    }
    @PostMapping("/{id}/editar") Object atualizar(@PathVariable UUID id, @Valid @ModelAttribute("form") MarombaForm form, BindingResult errors,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model, HttpServletRequest req) {
        return salvar(id, form, errors, p, model, req);
    }
    private Object salvar(UUID id, MarombaForm form, BindingResult errors, UsuarioAutenticado p, Model model, HttpServletRequest req) {
        var original = id == null ? null : service.consultar(id, p);
        if (original != null) service.exigirEdicao(original, p);
        String mensagem = errors.hasErrors() ? "Confira os campos: use uma data válida e quantidades inteiras, sem deixar campos vazios." : null;
        if (mensagem == null) {
            try {
                String url;
                if (id == null) {
                    var criados = service.criar(p, form);
                    url = criados.size() == 1 ? "/producao/" + criados.getFirst().getId() + "?salvo" : "/producao?salvo=" + criados.size();
                } else url = "/producao/" + service.editar(id, p, form).getId() + "?salvo";
                if (json(req)) return ResponseEntity.ok(Map.of("redirect", url));
                return new ModelAndView("redirect:" + url);
            } catch (RegraNegocioException e) { mensagem = e.getMessage(); }
            catch (DataIntegrityViolationException e) { mensagem = "Já existe Maromba deste formato para esta data e unidade. Consulte os registros antes de tentar novamente."; }
            catch (OptimisticLockingFailureException e) { mensagem = "O registro foi alterado por outra pessoa. Recarregue a página e confira os valores."; }
        }
        if (json(req)) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", mensagem));
        model.addAttribute("mensagemErro", mensagem); prepararForm(model, p, original);
        return new ModelAndView("producao/form", model.asMap(), HttpStatus.CONFLICT);
    }
    @PostMapping("/{id}/validar") String validar(@PathVariable UUID id, @RequestParam long versao,
            @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        try { service.validar(id, p, versao); flash.addFlashAttribute("sucesso", "Registro validado. Os indicadores já consideram estes valores."); }
        catch (RegraNegocioException | OptimisticLockingFailureException e) { flash.addFlashAttribute("falha", "Não foi possível validar esta revisão. Confira o estado atual do registro."); }
        return "redirect:/producao/" + id;
    }
    private void prepararForm(Model model, UsuarioAutenticado p, Maromba original) {
        model.addAttribute("registro", original);
        model.addAttribute("unidade", original == null ? unidades.findById(p.unidadeId()).orElseThrow() : original.getUnidade());
        model.addAttribute("formatos", original == null ? service.formatosAtivos() : List.of(original.getFormato()));
        model.addAttribute("hoje", service.hoje());
    }
    static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio) || fim.isAfter(inicio.plusYears(1))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um período de até um ano.");
    }
    private boolean json(HttpServletRequest req) { return Optional.ofNullable(req.getHeader("Accept")).orElse("").contains("application/json"); }
}
