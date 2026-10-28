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
    private final UsuarioRepository usuarios; private final UnidadeRepository unidades;
    public TelasController(UsuarioRepository usuarios, UnidadeRepository unidades) { this.usuarios = usuarios; this.unidades = unidades; }
    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/") String inicio(@AuthenticationPrincipal UsuarioAutenticado p) {
        return switch (p.perfil()) {
            case SUPERVISOR, GESTOR_PRODUCAO -> "redirect:/producao";
            case GESTOR_GERAL -> "redirect:/painel";
            case ADMINISTRADOR -> "redirect:/admin";
        };
    }
    @GetMapping("/admin") String admin(Model model) {
        model.addAttribute("usuarios", usuarios.findAllByOrderByNome());
        model.addAttribute("unidades", unidades.findAllByOrderByNome());
        return "admin";
    }
    @GetMapping("/acesso-negado") String negado(HttpServletResponse response, Model model) {
        response.setStatus(403); model.addAttribute("mensagem", "Seu perfil não permite acessar esta página."); return "erro";
    }
}
