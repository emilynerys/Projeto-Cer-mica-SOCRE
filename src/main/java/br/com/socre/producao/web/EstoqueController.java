package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.UnidadeRepository;
import br.com.socre.producao.producao.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// The "Estoque assado" sheet: yard counted by the Gestor de Produção, kiln read from the last map.
@Controller @RequestMapping("/estoque")
public class EstoqueController {
    private final EstoqueService estoque; private final UnidadeRepository unidades; private final ProducaoService producao; private final PanoramaService panorama;
    public EstoqueController(EstoqueService estoque, UnidadeRepository unidades, ProducaoService producao, PanoramaService panorama) {
        this.estoque = estoque; this.unidades = unidades; this.producao = producao; this.panorama = panorama;
    }
    @GetMapping String tela(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        var unidade = unidades.deTrabalho(p.unidadeId());
        model.addAttribute("unidade", unidade); model.addAttribute("quadro", estoque.quadro(unidade.getId()));
        model.addAttribute("historico", estoque.historico(unidade.getId(), 15)); model.addAttribute("formatos", producao.formatosAtivos());
        model.addAttribute("podeLancar", p.perfil() == Perfil.GESTOR_PRODUCAO); model.addAttribute("hoje", producao.hoje());
        model.addAttribute("panorama", panorama.de(p.unidadeId()));
        return "estoque";
    }
    @PostMapping String registrar(@AuthenticationPrincipal UsuarioAutenticado p, @RequestParam UUID unidade,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam Map<String, String> campos, RedirectAttributes flash) {
        try {
            estoque.registrar(p, unidade, data, numeros(campos, "primeira-"), numeros(campos, "segunda-"));
            flash.addFlashAttribute("sucesso", "Contagem do pátio salva.");
        } catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        return "redirect:/estoque";
    }
    /** "primeira-<format id>" = "20.360": thousands dots are accepted, as the factory writes them. */
    private static Map<UUID, Integer> numeros(Map<String, String> campos, String prefixo) {
        var mapa = new HashMap<UUID, Integer>();
        campos.forEach((k, v) -> {
            if (!k.startsWith(prefixo) || v == null || v.isBlank()) return;
            try { mapa.put(UUID.fromString(k.substring(prefixo.length())), Integer.parseInt(v.strip().replace(".", ""))); }
            catch (IllegalArgumentException e) { throw new RegraNegocioException("Use só números inteiros na contagem do pátio."); }
        });
        return mapa;
    }
}
