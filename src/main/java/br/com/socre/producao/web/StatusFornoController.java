package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// The kiln map drawn like the factory's "Status do Forno" spreadsheet: left side from the last line down, right side from line 1 up.
@Controller @RequestMapping("/status-forno")
public class StatusFornoController {
    private static final int NO_HISTORICO = 10;
    private final StatusFornoService status; private final UnidadeRepository unidades; private final ProducaoService producao;
    public StatusFornoController(StatusFornoService status, UnidadeRepository unidades, ProducaoService producao) {
        this.status = status; this.unidades = unidades; this.producao = producao;
    }
    /** One kiln line on screen; formato and estado are null for an empty line. */
    public record Celula(int linha, Formato formato, StatusForno.Estado estado, int quantidade) {}
    /** One row of the map: Esquerda and Direita lines side by side, as on the paper. */
    public record Fileira(Celula esquerda, Celula direita) {}

    @GetMapping String ver(@AuthenticationPrincipal UsuarioAutenticado p, @RequestParam(required = false) UUID unidade,
            @RequestParam(required = false) UUID id, Model model) {
        var pedido = id == null ? null : status.consultar(id, p);
        var escolhida = unidadeDaTela(p, pedido != null ? pedido.getUnidade().getId() : unidade, model);
        model.addAttribute("podeLancar", p.perfil() == Perfil.GESTOR_PRODUCAO || p.perfil() == Perfil.GESTOR_GERAL);
        if (escolhida == null) return "status-forno";
        var historico = status.recentes(escolhida.getId(), p, NO_HISTORICO);
        var atual = pedido != null ? pedido : historico.isEmpty() ? null : historico.getFirst();
        model.addAttribute("historico", historico); model.addAttribute("atual", atual);
        if (atual != null) {
            var porLinha = new HashMap<Integer, Celula>();
            atual.getLinhas().forEach(l -> porLinha.put(l.getLinha(), new Celula(l.getLinha(), l.getFormato(), l.getEstado(), l.getQuantidade())));
            model.addAttribute("fileiras", fileiras(atual.getUnidade().getLinhasForno(), porLinha));
            model.addAttribute("resumo", status.resumo(atual, producao.formatosAtivos()));
            // The loads only say how the kiln is now, so an older picture has nothing to be checked against.
            if (atual.getId().equals(historico.getFirst().getId())) model.addAttribute("conferencia", status.conferencia(atual, p));
        }
        model.addAttribute("estados", StatusForno.Estado.values());
        return "status-forno";
    }
    /** The new picture starts from the last one: from one day to the next, only a few lines change. */
    @GetMapping("/novo") String novo(@AuthenticationPrincipal UsuarioAutenticado p, @RequestParam(required = false) UUID unidade, Model model) {
        var escolhida = unidadeDaTela(p, unidade, model);
        var form = new StatusFornoForm();
        if (escolhida != null) {
            form.setIdUnidade(escolhida.getId());
            status.recentes(escolhida.getId(), p, 1).stream().findFirst().ifPresent(ultimo -> ultimo.getLinhas().forEach(l ->
                form.getLinhas().put(l.getLinha(), new StatusFornoForm.Linha(l.isVazia() ? null : l.getFormato().getId(), l.getEstado()))));
        }
        form.setDataHora(agora());
        return formulario(model, escolhida, form);
    }
    @PostMapping String registrar(@ModelAttribute StatusFornoForm form, @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        try {
            var s = status.registrar(p, form);
            flash.addFlashAttribute("sucesso", "Status do forno de " + new TemplateAdvice.Formatos().horario(s.getDataHora()) + " salvo.");
            return "redirect:/status-forno?unidade=" + s.getUnidade().getId();
        } catch (RegraNegocioException e) { model.addAttribute("mensagemErro", e.getMessage()); }
        return formulario(model, unidadeDaTela(p, form.getIdUnidade(), model), form);
    }

    private String formulario(Model model, Unidade unidade, StatusFornoForm form) {
        var ativos = producao.formatosAtivos();
        model.addAttribute("form", form); model.addAttribute("formatos", ativos);
        model.addAttribute("estados", StatusForno.Estado.values()); model.addAttribute("agora", agora());
        if (unidade != null) {
            var porId = new HashMap<UUID, Formato>(); ativos.forEach(f -> porId.put(f.getId(), f));
            var porLinha = new HashMap<Integer, Celula>();
            form.getLinhas().forEach((n, l) -> porLinha.put(n, new Celula(n, porId.get(l.getIdFormato()), l.getEstado(), 0)));
            model.addAttribute("fileiras", fileiras(unidade.getLinhasForno(), porLinha));
            model.addAttribute("totalLinhas", unidade.getLinhasForno());
        }
        return "status-forno-form";
    }
    /** Row i shows line i on the right and line (total + 1 − i) on the left: 46 next to 1, 24 next to 23. */
    static List<Fileira> fileiras(int total, Map<Integer, Celula> porLinha) {
        int metade = (total + 1) / 2;
        var lista = new ArrayList<Fileira>();
        for (int i = 1; i <= metade; i++) {
            int esquerda = total + 1 - i;
            lista.add(new Fileira(esquerda > metade ? celula(esquerda, porLinha) : null, celula(i, porLinha)));
        }
        return lista;
    }
    private static Celula celula(int n, Map<Integer, Celula> porLinha) { return porLinha.getOrDefault(n, new Celula(n, null, null, 0)); }
    /** Registro de Produção always sees its own unit; the other profiles choose one. */
    private Unidade unidadeDaTela(UsuarioAutenticado p, UUID unidade, Model model) {
        var todas = unidades.findAllByOrderByNome();
        UUID id = p.perfil() == Perfil.REGISTRO_PRODUCAO ? p.unidadeId() : unidade;
        var escolhida = todas.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(todas.isEmpty() ? null : todas.getFirst());
        if (p.perfil() == Perfil.REGISTRO_PRODUCAO && escolhida != null && !escolhida.getId().equals(id)) escolhida = null;
        model.addAttribute("unidades", todas); model.addAttribute("unidadeSelecionada", escolhida == null ? null : escolhida.getId());
        return escolhida;
    }
    private static LocalDateTime agora() { return LocalDateTime.now(RegistroApoio.FUSO).withSecond(0).withNano(0); }
}
