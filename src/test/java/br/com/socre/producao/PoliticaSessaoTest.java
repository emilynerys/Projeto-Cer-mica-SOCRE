package br.com.socre.producao;

import br.com.socre.producao.acesso.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PoliticaSessaoTest {
    private final PoliticaSessao politica = new PoliticaSessao();
    private final Instant inicio = Instant.parse("2026-09-26T12:00:00Z");
    @Test void supervisorUsaInatividadeDeTrintaDiasSemLimiteAbsoluto() {
        assertThat(politica.inatividadeSegundos(Perfil.SUPERVISOR)).isEqualTo(2592000);
        assertThat(politica.expirou(Perfil.SUPERVISOR, inicio, inicio.plus(Duration.ofDays(40)))).isFalse();
    }
    @Test void outrosPerfisExpiramExatamenteEmSeteDiasMesmoComAtividade() {
        for (var perfil : new Perfil[]{Perfil.GESTOR_PRODUCAO, Perfil.GESTOR_GERAL, Perfil.ADMINISTRADOR}) {
            assertThat(politica.inatividadeSegundos(perfil)).isEqualTo(604800);
            assertThat(politica.expirou(perfil, inicio, inicio.plus(Duration.ofDays(7)).minusSeconds(1))).isFalse();
            assertThat(politica.expirou(perfil, inicio, inicio.plus(Duration.ofDays(7)))).isTrue();
        }
    }
    @Test void sessaoSemDataDeAutenticacaoNaoEhAceita() {
        assertThat(politica.expirou(Perfil.SUPERVISOR, null, inicio)).isTrue();
    }
}
