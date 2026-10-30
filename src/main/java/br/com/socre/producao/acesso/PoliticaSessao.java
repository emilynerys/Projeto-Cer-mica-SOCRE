package br.com.socre.producao.acesso;

import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class PoliticaSessao {
    public static final String AUTENTICADO_EM = "socre.autenticadoEm";
    // Decided on 30/10/2026: Registro de Produção stays signed in while used at least every 3 days;
    // the other profiles sign in again 15 days after login, even when active.
    public static final Duration INATIVIDADE_REGISTRO = Duration.ofDays(3);
    public static final Duration LIMITE_DEMAIS_PERFIS = Duration.ofDays(15);
    public int inatividadeSegundos(Perfil perfil) {
        return Math.toIntExact((perfil == Perfil.REGISTRO_PRODUCAO ? INATIVIDADE_REGISTRO : LIMITE_DEMAIS_PERFIS).toSeconds());
    }
    public boolean expirou(Perfil perfil, Instant inicio, Instant agora) {
        return inicio == null || (perfil != Perfil.REGISTRO_PRODUCAO && !agora.isBefore(inicio.plus(LIMITE_DEMAIS_PERFIS)));
    }
}
