package br.com.socre.producao.acesso;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.session.web.http.CookieSerializer;

public class VerificarSessaoFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarios;
    private final PoliticaSessao politica;
    private final Clock clock;
    private final CookieSerializer cookies;
    public VerificarSessaoFilter(UsuarioRepository usuarios, PoliticaSessao politica, Clock clock, CookieSerializer cookies) {
        this.usuarios = usuarios; this.politica = politica; this.clock = clock; this.cookies = cookies;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado p) {
            var session = req.getSession(false);
            var u = usuarios.findById(p.id()).orElse(null);
            Instant inicio = session == null ? null : (Instant) session.getAttribute(PoliticaSessao.AUTENTICADO_EM);
            boolean valido = u != null && u.podeEntrar() && u.getVersaoAcesso() == p.versaoAcesso()
                    && u.getPerfil() == p.perfil() && Objects.equals(u.getSenhaHash(), p.password())
                    && Objects.equals(u.getUnidade() == null ? null : u.getUnidade().getId(), p.unidadeId())
                    && !politica.expirou(p.perfil(), inicio, clock.instant());
            if (!valido) {
                new SecurityContextLogoutHandler().logout(req, res, auth);
                if (req.getHeader("Accept") != null && req.getHeader("Accept").contains("application/json")) {
                    res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"Sua sessão terminou. Entre novamente antes de reenviar.\"}");
                } else res.sendRedirect("/login?expirou");
                return;
            }
            session.setMaxInactiveInterval(politica.inatividadeSegundos(p.perfil()));
            if (p.perfil() == Perfil.REGISTRO_PRODUCAO) {
                cookies.writeCookieValue(new CookieSerializer.CookieValue(req, res, session.getId()));
            }
        }
        chain.doFilter(req, res);
    }
}
