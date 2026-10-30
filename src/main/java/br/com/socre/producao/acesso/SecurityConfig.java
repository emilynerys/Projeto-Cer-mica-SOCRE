package br.com.socre.producao.acesso;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.session.web.http.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(UsuarioRepository usuarios) {
        return email -> usuarios.findByEmailIgnoreCase(email.strip()).filter(Usuario::podeEntrar)
                .map(UsuarioAutenticado::from).orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }
    @Bean CookieSerializer cookieSerializer(@Value("${socre.secure-cookie}") boolean secure) {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName("SOCRE_SESSION"); cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true); cookie.setUseSecureCookie(secure); cookie.setSameSite("Lax");
        cookie.setCookieMaxAge(Math.toIntExact(Duration.ofDays(30).toSeconds()));
        return cookie;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioRepository usuarios,
            PoliticaSessao politica, Clock clock, CookieSerializer cookies) throws Exception {
        http.authorizeHttpRequests(r -> r
                .requestMatchers("/login", "/css/**", "/js/**", "/error", "/favicon.svg").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/painel/**").hasAnyRole("GESTOR_PRODUCAO", "GESTOR_GERAL")
                .requestMatchers("/producao/maromba", "/producao/maromba/**", "/producao/enforna/nova", "/producao/desenforna/nova").hasRole("REGISTRO_PRODUCAO")
                .requestMatchers("/producao/validar-lote").hasRole("GESTOR_PRODUCAO")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/fornadas/**").hasAnyRole("REGISTRO_PRODUCAO", "GESTOR_PRODUCAO", "GESTOR_GERAL", "ADMINISTRADOR")
                .requestMatchers("/fornadas/**").hasAnyRole("REGISTRO_PRODUCAO", "GESTOR_PRODUCAO")
                // RN05: the Administrador reads production data but never writes it.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/producao/**").hasAnyRole("REGISTRO_PRODUCAO", "GESTOR_PRODUCAO", "GESTOR_GERAL", "ADMINISTRADOR")
                .requestMatchers("/producao/**").hasAnyRole("REGISTRO_PRODUCAO", "GESTOR_PRODUCAO", "GESTOR_GERAL")
                .anyRequest().authenticated())
            .formLogin(login -> login.loginPage("/login").usernameParameter("email")
                .successHandler((req, res, auth) -> {
                    var p = (UsuarioAutenticado) auth.getPrincipal();
                    var session = req.getSession();
                    session.setAttribute(PoliticaSessao.AUTENTICADO_EM, clock.instant());
                    session.setMaxInactiveInterval(politica.inatividadeSegundos(p.perfil()));
                    res.sendRedirect("/");
                }).failureUrl("/login?erro").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?saiu").deleteCookies("SOCRE_SESSION"))
            .exceptionHandling(e -> e.accessDeniedPage("/acesso-negado"))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
            .addFilterAfter(new VerificarSessaoFilter(usuarios, politica, clock, cookies), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
