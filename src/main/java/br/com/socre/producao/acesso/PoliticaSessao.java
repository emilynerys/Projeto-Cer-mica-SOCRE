package br.com.socre.producao.acesso;

import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class PoliticaSessao {
    public static final String AUTENTICADO_EM = "socre.autenticadoEm";
    public int inatividadeSegundos(Perfil perfil) {
        return Math.toIntExact(Duration.ofDays(perfil == Perfil.REGISTRO_PRODUCAO ? 30 : 7).toSeconds());
    }
    public boolean expirou(Perfil perfil, Instant inicio, Instant agora) {
        return inicio == null || (perfil != Perfil.REGISTRO_PRODUCAO && !agora.isBefore(inicio.plus(Duration.ofDays(7))));
    }
}
