package br.com.socre.producao;

import br.com.socre.producao.acesso.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PoliticaSessaoTest {
    private final PoliticaSessao politica = new PoliticaSessao();
    private final Instant inicio = Instant.parse("2026-09-26T12:00:00Z");
    @Test void registroDeProducaoUsaInatividadeDeTresDiasSemLimiteAbsoluto() {
        assertThat(politica.inatividadeSegundos(Perfil.REGISTRO_PRODUCAO)).isEqualTo(259200);
        assertThat(politica.expirou(Perfil.REGISTRO_PRODUCAO, inicio, inicio.plus(Duration.ofDays(40)))).isFalse();
    }
    @Test void outrosPerfisExpiramExatamenteEmQuinzeDiasMesmoComAtividade() {
        for (var perfil : new Perfil[]{Perfil.GESTOR_PRODUCAO, Perfil.GESTOR_GERAL, Perfil.ADMINISTRADOR}) {
            assertThat(politica.inatividadeSegundos(perfil)).isEqualTo(1296000);
            assertThat(politica.expirou(perfil, inicio, inicio.plus(Duration.ofDays(15)).minusSeconds(1))).isFalse();
            assertThat(politica.expirou(perfil, inicio, inicio.plus(Duration.ofDays(15)))).isTrue();
        }
    }
    @Test void sessaoSemDataDeAutenticacaoNaoEhAceita() {
        assertThat(politica.expirou(Perfil.REGISTRO_PRODUCAO, null, inicio)).isTrue();
    }
}
