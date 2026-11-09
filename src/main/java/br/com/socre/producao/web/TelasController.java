package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.UnidadeRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TelasController {
    private final UnidadeRepository unidades; private final br.com.socre.producao.producao.PanoramaService panorama;
    public TelasController(UnidadeRepository unidades, br.com.socre.producao.producao.PanoramaService panorama) { this.unidades = unidades; this.panorama = panorama; }
    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/") String inicio(@AuthenticationPrincipal UsuarioAutenticado p) {
        return switch (p.perfil()) {
            case REGISTRO_PRODUCAO -> "redirect:/lancamentos";
            case GESTOR_PRODUCAO -> "redirect:/lancamentos";
            case GESTOR_GERAL -> "redirect:/painel";
            case ADMINISTRADOR -> "redirect:/admin";
        };
    }
    /** Who registers starts here: the kiln situation and the three stages; the history has its own page. */
    @GetMapping("/lancamentos") String lancamentos(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        model.addAttribute("unidade", unidades.deTrabalho(p.unidadeId()));
        model.addAttribute("panorama", panorama.de(p.unidadeId()));
        return "producao/lancamentos";
    }
    @GetMapping("/acesso-negado") String negado(HttpServletResponse response, Model model) {
        response.setStatus(403); model.addAttribute("mensagem", "Seu perfil não permite acessar esta página."); return "erro";
    }
}
