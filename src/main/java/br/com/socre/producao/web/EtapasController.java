package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.producao.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Enforna and Desenforna screens: register, review, correct, validate and cancel.
@Controller @RequestMapping("/producao")
public class EtapasController {
    private final EnfornaService enfornas; private final DesenfornaService desenfornas; private final FornadaService fornadas;
    private final ProducaoService producao; private final UnidadeRepository unidades;
    public EtapasController(EnfornaService enfornas, DesenfornaService desenfornas, FornadaService fornadas, ProducaoService producao, UnidadeRepository unidades) {
        this.enfornas = enfornas; this.desenfornas = desenfornas; this.fornadas = fornadas; this.producao = producao; this.unidades = unidades;
    }
    public record Campo(String rotulo, String valor) {}
    private static final TemplateAdvice.Formatos FMT = new TemplateAdvice.Formatos();

    // ---------- Enforna ----------
    @GetMapping("/enforna/nova") String novaEnforna(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var f = new EnfornaForm(); f.setData(producao.hoje()); f.setChaveEnvio(UUID.randomUUID());
        producao.formatosAtivos().forEach(formato -> f.getItens().put(formato.getId(), new EnfornaForm.Item(0, formato.getTijolosPorLinha(), null)));
        var abertas = fornadas.paraEnforna(p.unidadeId());
        if (abertas.size() == 1) f.setIdFornada(abertas.getFirst().getId());
        model.addAttribute("form", f); prepararEnforna(model, p, null);
        return "producao/enforna-form";
    }
    @PostMapping("/enforna/nova") String criarEnforna(@Valid @ModelAttribute("form") EnfornaForm f, BindingResult errors,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        // An empty choice in the load list means "Nova fornada".
        f.setNovaFornada(f.getIdFornada() == null);
        if (!errors.hasErrors()) {
            try {
                var criados = enfornas.criar(p, f);
                flash.addFlashAttribute("sucesso", "Enforna registrada: " + criados.size() + " registro(s), um por formato, aguardando a conferência do Gestor de Produção.");
                return criados.size() == 1 ? "redirect:/producao/enforna/" + criados.getFirst().getId() : "redirect:/producao?etapa=ENFORNA";
            } catch (RuntimeException e) { tratar(e, model); }
        } else model.addAttribute("mensagemErro", "Confira os campos: use números inteiros, sem deixar campos vazios.");
        prepararEnforna(model, p, null);
        return "producao/enforna-form";
    }
    @GetMapping("/enforna/{id}") String detalheEnforna(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var e = enfornas.consultar(id, p);
        detalhe(model, p, e, "enforna", enfornas.historico(id), enfornas.podeAlterar(e, p), enfornas.avisos(e), List.of(
            new Campo("Fornada", e.getFornada().getIdentificacao()), new Campo("Formato", e.getFormato().getNome()),
            new Campo("Linhas enfornadas", FMT.inteiro(e.getLinhas())), new Campo("Tijolos por linha", FMT.inteiro(e.getTijolosPorLinha())),
            new Campo("Total enfornado", FMT.inteiro(e.getQuantidade()) + " tijolos")));
        return "producao/etapa-detalhe";
    }
    @GetMapping("/enforna/{id}/editar") String editarEnforna(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var e = enfornas.consultar(id, p); exigir(enfornas.podeAlterar(e, p));
        var f = new EnfornaForm(); f.setData(e.getData()); f.setChaveEnvio(e.getChaveEnvio()); f.setVersao(e.getVersao()); f.setIdFornada(e.getFornada().getId());
        f.getItens().put(e.getFormato().getId(), new EnfornaForm.Item(e.getLinhas(), e.getTijolosPorLinha(), e.getQuantidade()));
        model.addAttribute("form", f); prepararEnforna(model, p, e);
        return "producao/enforna-form";
    }
    @PostMapping("/enforna/{id}/editar") String atualizarEnforna(@PathVariable UUID id, @Valid @ModelAttribute("form") EnfornaForm f, BindingResult errors,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        var e = enfornas.consultar(id, p); exigir(enfornas.podeAlterar(e, p));
        if (!errors.hasErrors()) {
            try { enfornas.editar(id, p, f); flash.addFlashAttribute("sucesso", "Correção salva. O valor anterior continua no histórico."); return "redirect:/producao/enforna/" + id; }
            catch (RuntimeException ex) { tratar(ex, model); }
        } else model.addAttribute("mensagemErro", "Confira os campos: use números inteiros, sem deixar campos vazios.");
        prepararEnforna(model, p, e);
        return "producao/enforna-form";
    }
    @PostMapping("/enforna/{id}/validar") String validarEnforna(@PathVariable UUID id, @RequestParam long versao, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        return acao(() -> enfornas.validar(id, p, versao), "Registro validado. Os indicadores já consideram estes valores.", "/producao/enforna/" + id, flash);
    }
    @PostMapping("/enforna/{id}/cancelar") String cancelarEnforna(@PathVariable UUID id, @RequestParam long versao, @RequestParam(required = false) UUID idMotivo,
            @RequestParam(required = false) String textoMotivo, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        return acao(() -> enfornas.cancelar(id, p, versao, idMotivo, textoMotivo), "Registro cancelado. Ele continua disponível para consulta, mas sai dos indicadores.", "/producao/enforna/" + id, flash);
    }
    private void prepararEnforna(Model model, UsuarioAutenticado p, Enforna original) {
        model.addAttribute("registro", original);
        model.addAttribute("formatos", original == null ? producao.formatosAtivos() : List.of(original.getFormato()));
        var unidade = original == null ? unidades.findById(p.unidadeId()).orElseThrow() : original.getUnidade();
        model.addAttribute("unidade", unidade);
        model.addAttribute("fornadasDisponiveis", original == null ? fornadas.paraEnforna(unidade.getId()) : List.of(original.getFornada()));
        model.addAttribute("podeAbrirNova", original == null && fornadas.podeAbrirNova(unidade.getId()));
        model.addAttribute("motivosCorrecao", producao.motivos(Motivo.Tipo.CORRECAO));
        model.addAttribute("hoje", producao.hoje());
    }

    // ---------- Desenforna ----------
    @GetMapping("/desenforna/nova") String novaDesenforna(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var f = new DesenfornaForm(); f.setData(producao.hoje()); f.setChaveEnvio(UUID.randomUUID());
        producao.formatosAtivos().forEach(formato -> f.getItens().put(formato.getId(), new DesenfornaForm.Item(0, 0, 0, 0)));
        var abertas = fornadas.emAndamento(p.unidadeId());
        // The load being emptied is the oldest in progress (RN08).
        if (!abertas.isEmpty()) f.setIdFornada(abertas.getFirst().getId());
        model.addAttribute("form", f); prepararDesenforna(model, p, null);
        return "producao/desenforna-form";
    }
    @PostMapping("/desenforna/nova") String criarDesenforna(@Valid @ModelAttribute("form") DesenfornaForm f, BindingResult errors,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                var criados = desenfornas.criar(p, f);
                flash.addFlashAttribute("sucesso", "Desenforna registrada: " + criados.size() + " registro(s), um por formato, aguardando a conferência do Gestor de Produção.");
                return criados.size() == 1 ? "redirect:/producao/desenforna/" + criados.getFirst().getId() : "redirect:/producao?etapa=DESENFORNA";
            } catch (RuntimeException e) { tratar(e, model); }
        } else model.addAttribute("mensagemErro", "Confira os campos: use números inteiros, sem deixar campos vazios.");
        prepararDesenforna(model, p, null);
        return "producao/desenforna-form";
    }
    @GetMapping("/desenforna/{id}") String detalheDesenforna(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var d = desenfornas.consultar(id, p);
        detalhe(model, p, d, "desenforna", desenfornas.historico(id), desenfornas.podeAlterar(d, p), desenfornas.avisos(d), List.of(
            new Campo("Fornada", d.getFornada().getIdentificacao()), new Campo("Formato", d.getFormato().getNome()),
            new Campo("Linhas desenfornadas", FMT.inteiro(d.getLinhas())), new Campo("1ª qualidade", FMT.inteiro(d.getPrimeira()) + " tijolos"),
            new Campo("2ª qualidade", FMT.inteiro(d.getSegunda()) + " tijolos"), new Campo("Cru que saiu do forno", FMT.inteiro(d.getCru()) + " tijolos"),
            new Campo("Produção acabada (1ª + 2ª)", FMT.inteiro(d.getAcabado()) + " tijolos")));
        return "producao/etapa-detalhe";
    }
    @GetMapping("/desenforna/{id}/editar") String editarDesenforna(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var d = desenfornas.consultar(id, p); exigir(desenfornas.podeAlterar(d, p));
        var f = new DesenfornaForm(); f.setData(d.getData()); f.setChaveEnvio(d.getChaveEnvio()); f.setVersao(d.getVersao()); f.setIdFornada(d.getFornada().getId());
        f.getItens().put(d.getFormato().getId(), new DesenfornaForm.Item(d.getLinhas(), d.getPrimeira(), d.getSegunda(), d.getCru()));
        model.addAttribute("form", f); prepararDesenforna(model, p, d);
        return "producao/desenforna-form";
    }
    @PostMapping("/desenforna/{id}/editar") String atualizarDesenforna(@PathVariable UUID id, @Valid @ModelAttribute("form") DesenfornaForm f, BindingResult errors,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        var d = desenfornas.consultar(id, p); exigir(desenfornas.podeAlterar(d, p));
        if (!errors.hasErrors()) {
            try { desenfornas.editar(id, p, f); flash.addFlashAttribute("sucesso", "Correção salva. O valor anterior continua no histórico."); return "redirect:/producao/desenforna/" + id; }
            catch (RuntimeException ex) { tratar(ex, model); }
        } else model.addAttribute("mensagemErro", "Confira os campos: use números inteiros, sem deixar campos vazios.");
        prepararDesenforna(model, p, d);
        return "producao/desenforna-form";
    }
    @PostMapping("/desenforna/{id}/validar") String validarDesenforna(@PathVariable UUID id, @RequestParam long versao, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        return acao(() -> desenfornas.validar(id, p, versao), "Registro validado. Os indicadores já consideram estes valores.", "/producao/desenforna/" + id, flash);
    }
    @PostMapping("/desenforna/{id}/cancelar") String cancelarDesenforna(@PathVariable UUID id, @RequestParam long versao, @RequestParam(required = false) UUID idMotivo,
            @RequestParam(required = false) String textoMotivo, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        return acao(() -> desenfornas.cancelar(id, p, versao, idMotivo, textoMotivo), "Registro cancelado. Ele continua disponível para consulta, mas sai dos indicadores.", "/producao/desenforna/" + id, flash);
    }
    private void prepararDesenforna(Model model, UsuarioAutenticado p, Desenforna original) {
        model.addAttribute("registro", original);
        model.addAttribute("formatos", original == null ? producao.formatosAtivos() : List.of(original.getFormato()));
        var unidade = original == null ? unidades.findById(p.unidadeId()).orElseThrow() : original.getUnidade();
        model.addAttribute("unidade", unidade);
        model.addAttribute("fornadasDisponiveis", original == null ? fornadas.emAndamento(unidade.getId()) : List.of(original.getFornada()));
        model.addAttribute("motivosCorrecao", producao.motivos(Motivo.Tipo.CORRECAO));
        model.addAttribute("hoje", producao.hoje());
    }

    // ---------- shared ----------
    private void detalhe(Model model, UsuarioAutenticado p, RegistroEtapa r, String caminho, Object historico, boolean podeAlterar, List<String> avisos, List<Campo> campos) {
        model.addAttribute("registro", r); model.addAttribute("caminho", caminho); model.addAttribute("campos", campos);
        model.addAttribute("historico", historico); model.addAttribute("podeEditar", podeAlterar);
        model.addAttribute("podeValidar", r.getSituacao() == Situacao.PENDENTE && p.perfil() == Perfil.GESTOR_PRODUCAO);
        // RN14: the Gestor sees the warnings the Registro de Produção confirmed.
        model.addAttribute("avisos", p.perfil() == Perfil.REGISTRO_PRODUCAO ? List.of() : avisos);
        model.addAttribute("motivosCancelamento", producao.motivos(Motivo.Tipo.CANCELAMENTO));
    }
    private void tratar(RuntimeException e, Model model) {
        switch (e) {
            case AvisosPendentesException a -> model.addAttribute("avisos", a.getAvisos());
            case RegraNegocioException r -> model.addAttribute("mensagemErro", r.getMessage());
            case DataIntegrityViolationException d -> model.addAttribute("mensagemErro", "Já existe registro desta etapa, formato e data. Consulte os registros antes de tentar novamente.");
            case OptimisticLockingFailureException o -> model.addAttribute("mensagemErro", "O registro foi alterado por outra pessoa. Recarregue a página e confira os valores.");
            default -> throw e;
        }
    }
    private String acao(Runnable r, String sucesso, String destino, RedirectAttributes flash) {
        try { r.run(); flash.addFlashAttribute("sucesso", sucesso); }
        catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        catch (OptimisticLockingFailureException e) { flash.addFlashAttribute("falha", "Não foi possível concluir esta revisão. Confira o estado atual do registro."); }
        return "redirect:" + destino;
    }
    private static void exigir(boolean pode) { if (!pode) throw new AccessDeniedException("Seu perfil não permite esta operação."); }
}
