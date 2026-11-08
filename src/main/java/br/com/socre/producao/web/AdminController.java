package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.cadastro.CadastroService.*;
import br.com.socre.producao.producao.RegraNegocioException;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// RF22, RF26 and RF32: the Administrador's screens. Carolina is the only unit for now, so units are not created here.
@Controller @RequestMapping("/admin")
public class AdminController {
    private final CadastroService cadastro; private final UsuarioRepository usuarios; private final UnidadeRepository unidades;
    private final FormatoRepository formatos; private final MotivoRepository motivos;
    private final br.com.socre.producao.auditoria.AuditoriaRepository auditoria; private final br.com.socre.producao.producao.PanoramaService panorama;
    public AdminController(CadastroService cadastro, UsuarioRepository usuarios, UnidadeRepository unidades, FormatoRepository formatos, MotivoRepository motivos,
            br.com.socre.producao.auditoria.AuditoriaRepository auditoria, br.com.socre.producao.producao.PanoramaService panorama) {
        this.cadastro = cadastro; this.usuarios = usuarios; this.unidades = unidades; this.formatos = formatos; this.motivos = motivos;
        this.auditoria = auditoria; this.panorama = panorama;
    }
    @GetMapping String admin(Model model) {
        model.addAttribute("usuarios", usuarios.findAllByOrderByNome());
        model.addAttribute("unidades", unidades.findAllByOrderByNome());
        model.addAttribute("formatos", formatos.findAllByOrderByNome());
        model.addAttribute("motivos", motivos.findAllByOrderByTipoAscDescricaoAsc());
        model.addAttribute("tiposMotivo", Motivo.Tipo.values());
        model.addAttribute("panorama", panorama.de(null));
        return "admin";
    }
    /** Everything each person did, newest first, read from the audit trail that nobody can change. */
    @GetMapping("/atividade") String atividade(Model model) {
        model.addAttribute("eventos", auditoria.findTop150ByOrderByDataHoraDesc());
        return "atividade";
    }

    @GetMapping("/usuarios/novo") String novoUsuario(Model model) {
        return telaUsuario(model, null, new UsuarioForm(null, null, null, null, true, null));
    }
    @PostMapping("/usuarios") String criarUsuario(@ModelAttribute("form") UsuarioForm form, @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        try {
            var u = cadastro.criarUsuario(p, form);
            flash.addFlashAttribute("sucesso", "Conta de " + u.getNome() + " criada. Passe o login e a senha provisória: no primeiro acesso a pessoa cria a própria senha.");
            return "redirect:/admin";
        } catch (RegraNegocioException e) { model.addAttribute("mensagemErro", e.getMessage()); }
        return telaUsuario(model, null, semSenha(form));
    }
    @GetMapping("/usuarios/{id}") String verUsuario(@PathVariable UUID id, Model model) {
        var u = cadastro.usuario(id);
        return telaUsuario(model, u, new UsuarioForm(u.getNome(), u.getLogin(), u.getPerfil(), u.getUnidade() == null ? null : u.getUnidade().getId(), u.isAtivo(), null));
    }
    @PostMapping("/usuarios/{id}") String editarUsuario(@PathVariable UUID id, @ModelAttribute("form") UsuarioForm form, @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        try {
            var u = cadastro.editarUsuario(p, id, form);
            flash.addFlashAttribute("sucesso", "Conta de " + u.getNome() + " atualizada.");
            return "redirect:/admin";
        } catch (RegraNegocioException e) { model.addAttribute("mensagemErro", e.getMessage()); }
        return telaUsuario(model, cadastro.usuario(id), semSenha(form));
    }
    @PostMapping("/usuarios/{id}/senha") String redefinirSenha(@PathVariable UUID id, @RequestParam(required = false) String senha, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        try {
            var u = cadastro.redefinirSenha(p, id, senha);
            flash.addFlashAttribute("sucesso", "Senha de " + u.getNome() + " redefinida. Essa pessoa entra com a senha provisória e cria uma nova.");
            return "redirect:/admin";
        } catch (RegraNegocioException e) { flash.addFlashAttribute("mensagemErro", e.getMessage()); }
        return "redirect:/admin/usuarios/" + id;
    }
    private String telaUsuario(Model model, Usuario usuario, UsuarioForm form) {
        model.addAttribute("usuario", usuario); model.addAttribute("form", form);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("unidades", unidades.findAllByOrderByNome().stream().filter(Unidade::isAtiva).toList());
        return "admin-usuario";
    }
    private static UsuarioForm semSenha(UsuarioForm f) { return new UsuarioForm(f.nome(), f.login(), f.perfil(), f.idUnidade(), f.ativo(), null); }

    @GetMapping("/formatos/novo") String novoFormato(Model model) {
        return telaFormato(model, null, new FormatoForm(null, null, true));
    }
    @PostMapping("/formatos") String criarFormato(@ModelAttribute("form") FormatoForm form, @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("sucesso", "Formato " + cadastro.criarFormato(p, form).getNome() + " criado.");
            return "redirect:/admin";
        } catch (RegraNegocioException e) { model.addAttribute("mensagemErro", e.getMessage()); }
        return telaFormato(model, null, form);
    }
    @GetMapping("/formatos/{id}") String verFormato(@PathVariable UUID id, Model model) {
        var f = cadastro.formato(id);
        return telaFormato(model, f, new FormatoForm(f.getNome(), f.getTijolosPorLinha(), f.isAtivo()));
    }
    @PostMapping("/formatos/{id}") String editarFormato(@PathVariable UUID id, @ModelAttribute("form") FormatoForm form, @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("sucesso", "Formato " + cadastro.editarFormato(p, id, form).getNome() + " atualizado. Os lançamentos já feitos não mudam.");
            return "redirect:/admin";
        } catch (RegraNegocioException e) { model.addAttribute("mensagemErro", e.getMessage()); }
        return telaFormato(model, cadastro.formato(id), form);
    }
    private String telaFormato(Model model, Formato formato, FormatoForm form) {
        model.addAttribute("formato", formato); model.addAttribute("form", form);
        return "admin-formato";
    }

    @PostMapping("/unidades/{id}/forno") String alterarForno(@PathVariable UUID id, @RequestParam(required = false) Integer linhas, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        try {
            var u = cadastro.alterarLinhasForno(p, id, linhas);
            flash.addFlashAttribute("sucesso", "O forno de " + u.getNome() + " agora tem " + u.getLinhasForno() + " linhas.");
        } catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        return "redirect:/admin";
    }
    @PostMapping("/motivos") String criarMotivo(@ModelAttribute MotivoForm form, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("sucesso", "Motivo “" + cadastro.criarMotivo(p, form).getDescricao() + "” criado.");
        } catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        return "redirect:/admin";
    }
    @PostMapping("/motivos/{id}/alternar") String alternarMotivo(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado p, RedirectAttributes flash) {
        try {
            var m = cadastro.alternarMotivo(p, id);
            flash.addFlashAttribute("sucesso", "Motivo “" + m.getDescricao() + "” " + (m.isAtivo() ? "voltou para a lista." : "saiu da lista."));
        } catch (RegraNegocioException e) { flash.addFlashAttribute("falha", e.getMessage()); }
        return "redirect:/admin";
    }
}
