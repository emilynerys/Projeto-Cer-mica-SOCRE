package br.com.socre.producao.web;

import br.com.socre.producao.acesso.UsuarioAutenticado;
import br.com.socre.producao.cadastro.UnidadeRepository;
import br.com.socre.producao.producao.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// RF41: the Administrador checks the file first; only a file with no errors can be imported.
@Controller @RequestMapping("/admin/importacao")
public class ImportacaoController {
    private final ImportacaoService importacao; private final UnidadeRepository unidades;
    public ImportacaoController(ImportacaoService importacao, UnidadeRepository unidades) { this.importacao = importacao; this.unidades = unidades; }
    @GetMapping String tela(@RequestParam(required = false) UUID unidade, Model model) {
        return preparar(model, unidade, null, null);
    }
    @PostMapping("/conferir") String conferir(@RequestParam(required = false) UUID unidade, @RequestParam(required = false) String texto,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        return preparar(model, unidade, texto, importacao.conferir(p, escolhida(unidade), texto));
    }
    @PostMapping String importar(@RequestParam(required = false) UUID unidade, @RequestParam(required = false) String texto,
            @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        var feito = importacao.importar(p, escolhida(unidade), texto);
        if (!feito.podeImportar()) return preparar(model, unidade, texto, feito);
        flash.addFlashAttribute("sucesso", feito.total() + " lançamento(s) importado(s) como validados.");
        return "redirect:/admin/importacao?unidade=" + escolhida(unidade);
    }
    private String preparar(Model model, UUID unidade, String texto, ImportacaoService.Conferencia conferencia) {
        var id = escolhida(unidade);
        model.addAttribute("unidades", unidades.findAllByOrderByNome()); model.addAttribute("unidadeSelecionada", id);
        model.addAttribute("jaImportado", id != null && importacao.jaImportado(id));
        model.addAttribute("cabecalho", ImportacaoService.CABECALHO); model.addAttribute("texto", texto); model.addAttribute("conferencia", conferencia);
        return "importacao";
    }
    private UUID escolhida(UUID unidade) {
        if (unidade != null) return unidade;
        var todas = unidades.findAllByOrderByNome();
        return todas.isEmpty() ? null : todas.getFirst().getId();
    }
}
