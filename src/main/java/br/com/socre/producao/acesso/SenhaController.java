package br.com.socre.producao.acesso;

import jakarta.servlet.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// Each person changes the own password; the first access with a provisional one ends here (VerificarSessaoFilter).
@Controller @RequestMapping("/senha")
public class SenhaController {
    static final int SENHA_MINIMA = 8, SENHA_MAXIMA = 72;
    private final UsuarioRepository usuarios; private final PasswordEncoder encoder;
    public SenhaController(UsuarioRepository usuarios, PasswordEncoder encoder) { this.usuarios = usuarios; this.encoder = encoder; }
    @GetMapping String tela(@AuthenticationPrincipal UsuarioAutenticado p, Model model) {
        model.addAttribute("obrigatoria", usuarios.findById(p.id()).map(Usuario::isTrocarSenha).orElse(false));
        return "senha";
    }
    @PostMapping @Transactional
    String trocar(@AuthenticationPrincipal UsuarioAutenticado p, @RequestParam(required = false) String atual, @RequestParam(required = false) String nova,
            @RequestParam(required = false) String confirmacao, Model model, HttpServletRequest req, HttpServletResponse res) {
        var u = usuarios.findById(p.id()).orElseThrow();
        String erro = null;
        if (atual == null || !encoder.matches(atual, u.getSenhaHash())) erro = "A senha atual não confere.";
        else if (nova == null || nova.length() < SENHA_MINIMA) erro = "A senha nova precisa ter ao menos " + SENHA_MINIMA + " caracteres.";
        else if (nova.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SENHA_MAXIMA) erro = "A senha nova pode ter até " + SENHA_MAXIMA + " caracteres.";
        else if (!nova.equals(confirmacao)) erro = "A confirmação não é igual à senha nova.";
        else if (encoder.matches(nova, u.getSenhaHash())) erro = "Escolha uma senha diferente da atual.";
        if (erro != null) {
            model.addAttribute("mensagemErro", erro); model.addAttribute("obrigatoria", u.isTrocarSenha());
            return "senha";
        }
        u.trocarSenha(encoder.encode(nova));
        usuarios.flush();
        // The new hash ends the open sessions, this one included.
        new SecurityContextLogoutHandler().logout(req, res, null);
        return "redirect:/login?senhaTrocada";
    }
}
