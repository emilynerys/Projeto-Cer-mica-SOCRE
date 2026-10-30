package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
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
    private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas;
    private final EnfornaService enfornaService; private final DesenfornaService desenfornaService;
    public ProducaoController(ProducaoService service, MarombaRepository marombas, UnidadeRepository unidades, EnfornaRepository enfornas,
            DesenfornaRepository desenfornas, EnfornaService enfornaService, DesenfornaService desenfornaService) {
        this.service = service; this.marombas = marombas; this.unidades = unidades; this.enfornas = enfornas; this.desenfornas = desenfornas;
        this.enfornaService = enfornaService; this.desenfornaService = desenfornaService;
    }
    /** One line of the list, whatever the stage; the key "tabela:id" feeds the batch validation (RF36). */
    public record Linha(String etapa, String url, LocalDate data, String unidade, String fornada, String formato, String resumo,
            Situacao situacao, Instant criadoEm, String chave) {}
    private static final TemplateAdvice.Formatos FMT = new TemplateAdvice.Formatos();

    @GetMapping String lista(@AuthenticationPrincipal UsuarioAutenticado p, Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Situacao situacao, @RequestParam(required = false) String etapa,
            @RequestParam(defaultValue = "0") int pagina) {
        if (inicio == null) inicio = service.hoje().minusDays(14);
        if (fim == null) fim = service.hoje();
        validarPeriodo(inicio, fim);
        boolean registroProducao = p.perfil() == Perfil.REGISTRO_PRODUCAO;
        UUID autor = registroProducao ? p.id() : null, unidade = registroProducao ? p.unidadeId() : null;
        var linhas = new ArrayList<Linha>();
        if (etapa == null || etapa.equals("MAROMBA"))
            for (var m : marombas.listar(inicio, fim, autor, unidade, situacao))
                linhas.add(new Linha("Maromba", "/producao/" + m.getId(), m.getData(), m.getUnidade().getNome(), null, m.getFormato().getNome(),
                    FMT.inteiro(m.getQuantidade()) + " tijolos", m.getSituacao(), m.getCriadoEm(), "maromba:" + m.getId()));
        if (etapa == null || etapa.equals("ENFORNA"))
            for (var e : enfornas.consultar(inicio, fim, autor, unidade, situacao))
                linhas.add(new Linha("Enforna", "/producao/enforna/" + e.getId(), e.getData(), e.getUnidade().getNome(), e.getFornada().getIdentificacao(),
                    e.getFormato().getNome(), e.getLinhas() + " linhas · " + FMT.inteiro(e.getQuantidade()) + " tijolos", e.getSituacao(), e.getCriadoEm(), "enforna:" + e.getId()));
        if (etapa == null || etapa.equals("DESENFORNA"))
            for (var d : desenfornas.consultar(inicio, fim, autor, unidade, situacao))
                linhas.add(new Linha("Desenforna", "/producao/desenforna/" + d.getId(), d.getData(), d.getUnidade().getNome(), d.getFornada().getIdentificacao(),
                    d.getFormato().getNome(), d.getLinhas() + " linhas · 1ª " + FMT.inteiro(d.getPrimeira()) + " · 2ª " + FMT.inteiro(d.getSegunda())
                    + " · Cru " + FMT.inteiro(d.getCru()), d.getSituacao(), d.getCriadoEm(), "desenforna:" + d.getId()));
        // Pending first, then the most recent production day.
        linhas.sort(Comparator.comparing((Linha l) -> l.situacao() != Situacao.PENDENTE).thenComparing(Linha::data, Comparator.reverseOrder())
            .thenComparing(Linha::criadoEm, Comparator.reverseOrder()).thenComparing(Linha::formato));
        int tamanho = 30, pag = Math.max(0, pagina), de = Math.min(pag * tamanho, linhas.size());
        var resultado = new PageImpl<>(linhas.subList(de, Math.min(de + tamanho, linhas.size())), PageRequest.of(pag, tamanho), linhas.size());
        model.addAttribute("registros", resultado); model.addAttribute("inicio", inicio); model.addAttribute("fim", fim);
        model.addAttribute("situacao", situacao); model.addAttribute("etapa", etapa); model.addAttribute("registroProducao", registroProducao);
        model.addAttribute("podeValidarLote", p.perfil() == Perfil.GESTOR_PRODUCAO);
        return "producao/lista";
    }
    /** RF36: validates the selected Pending records of any stage at once. */
    @PostMapping("/validar-lote") String validarLote(@RequestParam(required = false) List<String> ids, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        var porTabela = new HashMap<String, List<UUID>>();
        for (var chave : ids == null ? List.<String>of() : ids) {
            var partes = chave.split(":", 2);
            if (partes.length == 2) porTabela.computeIfAbsent(partes[0], k -> new ArrayList<>()).add(UUID.fromString(partes[1]));
        }
        int validados = service.validarLote(porTabela.getOrDefault("maromba", List.of()), p)
            + enfornaService.validarLote(porTabela.getOrDefault("enforna", List.of()), p)
            + desenfornaService.validarLote(porTabela.getOrDefault("desenforna", List.of()), p);
        flash.addFlashAttribute("sucesso", validados == 0 ? "Nenhum registro pendente foi selecionado." : validados + " registro(s) validado(s).");
        return "redirect:/producao";
    }
    @GetMapping("/maromba") String nova(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var f = new MarombaForm(); f.setData(service.hoje()); f.setChaveEnvio(UUID.randomUUID());
        service.formatosAtivos().forEach(formato -> f.getQuantidades().put(formato.getId(), 0));
        model.addAttribute("form", f); prepararForm(model, p, null); return "producao/form";
    }
    @GetMapping("/{id}") String detalhe(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var m = service.consultar(id, p); model.addAttribute("registro", m);
        model.addAttribute("historico", service.historico(id));
        model.addAttribute("podeEditar", service.podeAlterar(m, p));
        model.addAttribute("motivosCancelamento", service.motivos(Motivo.Tipo.CANCELAMENTO));
        model.addAttribute("podeValidar", m.getSituacao() == Situacao.PENDENTE && p.perfil() == Perfil.GESTOR_PRODUCAO);
        return "producao/detalhe";
    }
    @GetMapping("/{id}/editar") String editar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var m = service.consultar(id, p); exigirAlteracao(m, p);
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
        if (original != null) exigirAlteracao(original, p);
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
    // RN16: cancels a record that should not exist, with a reason from the list.
    @PostMapping("/{id}/cancelar") String cancelar(@PathVariable UUID id, @RequestParam long versao, @RequestParam(required = false) UUID idMotivo,
            @RequestParam(required = false) String textoMotivo, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        try { service.cancelar(id, p, versao, idMotivo, textoMotivo); flash.addFlashAttribute("sucesso", "Registro cancelado. Ele continua disponível para consulta, mas sai dos indicadores."); }
        catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        catch (OptimisticLockingFailureException e) { flash.addFlashAttribute("falha", "Não foi possível cancelar esta revisão. Confira o estado atual do registro."); }
        return "redirect:/producao/" + id;
    }
    private void exigirAlteracao(Maromba m, UsuarioAutenticado p) {
        if (!service.podeAlterar(m, p)) throw new org.springframework.security.access.AccessDeniedException("Seu perfil não permite esta operação.");
    }
    private void prepararForm(Model model, UsuarioAutenticado p, Maromba original) {
        model.addAttribute("registro", original);
        model.addAttribute("motivosCorrecao", service.motivos(Motivo.Tipo.CORRECAO));
        model.addAttribute("unidade", original == null ? unidades.findById(p.unidadeId()).orElseThrow() : original.getUnidade());
        model.addAttribute("formatos", original == null ? service.formatosAtivos() : List.of(original.getFormato()));
        model.addAttribute("hoje", service.hoje());
    }
    static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio) || fim.isAfter(inicio.plusYears(1))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um período de até um ano.");
    }
    private boolean json(HttpServletRequest req) { return Optional.ofNullable(req.getHeader("Accept")).orElse("").contains("application/json"); }
}
