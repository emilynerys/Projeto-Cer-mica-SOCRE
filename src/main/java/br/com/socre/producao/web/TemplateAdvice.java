package br.com.socre.producao.web;

import br.com.socre.producao.acesso.UsuarioAutenticado;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class TemplateAdvice {
    private final Environment ambiente;
    public TemplateAdvice(Environment ambiente) { this.ambiente = ambiente; }
    @ModelAttribute void atributos(Model model, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        model.addAttribute("usuarioAtual", usuario);
        model.addAttribute("ambienteDev", ambiente.matchesProfiles("dev"));
        model.addAttribute("fmt", new Formatos());
    }
    public static class Formatos {
        public String inteiro(long valor) { return NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")).format(valor); }
        public String milheiros(long valor) {
            var f = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
            f.setMinimumFractionDigits(1); f.setMaximumFractionDigits(3); return f.format(valor / 1000.0);
        }
        public String data(LocalDate valor) { return valor == null ? "—" : valor.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
        public String horario(Instant valor) { return valor == null ? "—" : DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Fortaleza")).format(valor); }
    }
}
