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
    private final UnidadeRepository unidades;
    public TelasController(UnidadeRepository unidades) { this.unidades = unidades; }
    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/") String inicio(@AuthenticationPrincipal UsuarioAutenticado p) {
        return switch (p.perfil()) {
            case REGISTRO_PRODUCAO -> "redirect:/lancamentos";
            case GESTOR_PRODUCAO -> "redirect:/producao";
            case GESTOR_GERAL -> "redirect:/painel";
            case ADMINISTRADOR -> "redirect:/admin";
        };
    }
    /** The Registro de Produção starts here: only the three stages to register; the history has its own page. */
    @GetMapping("/lancamentos") String lancamentos(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        model.addAttribute("unidade", unidades.findById(p.unidadeId()).orElseThrow());
        return "producao/lancamentos";
    }
    @GetMapping("/acesso-negado") String negado(HttpServletResponse response, Model model) {
        response.setStatus(403); model.addAttribute("mensagem", "Seu perfil não permite acessar esta página."); return "erro";
    }
}
