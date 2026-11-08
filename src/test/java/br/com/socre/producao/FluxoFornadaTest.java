package br.com.socre.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import static br.com.socre.producao.cadastro.Formato.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class FluxoFornadaTest {
    @Autowired EnfornaService enfornas;
    @Autowired DesenfornaService desenfornas;
    @Autowired FornadaService fornadas;
    @Autowired ProducaoService marombas;
    @Autowired InventarioService inventario;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeRepository unidades;
    @Autowired MotivoRepository motivos;
    private UsuarioAutenticado registro, gestor, geral, admin;
    private Unidade unidade;

    @BeforeEach void preparar() {
        unidade = unidades.saveAndFlush(new Unidade(UUID.randomUUID(), "Unidade de teste " + UUID.randomUUID(), 46));
        registro = usuario(Perfil.REGISTRO_PRODUCAO, unidade);
        gestor = usuario(Perfil.GESTOR_PRODUCAO, null);
        geral = usuario(Perfil.GESTOR_GERAL, null);
        admin = usuario(Perfil.ADMINISTRADOR, null);
    }
    private UsuarioAutenticado usuario(Perfil perfil, Unidade u) {
        var id = UUID.randomUUID();
        return UsuarioAutenticado.from(usuarios.saveAndFlush(new Usuario(id, perfil.getDescricao(), id + "@teste.local", "x", perfil, u)));
    }
    private UUID motivo(Motivo.Tipo tipo, String descricao) {
        return motivos.findByTipoAndAtivoTrueOrderByDescricao(tipo).stream().filter(m -> m.getDescricao().equals(descricao)).findFirst().orElseThrow().getId();
    }
    private LocalDate hoje() { return marombas.hoje(); }
    private EnfornaForm enforna(Fornada fornada, int linhas8f, Integer quantidade8f) {
        var f = new EnfornaForm(); f.setData(hoje()); f.setChaveEnvio(UUID.randomUUID());
        // Without a load, the form says nothing and the Enforna goes to the load open for Enforna.
        if (fornada != null) f.setIdFornada(fornada.getId());
        f.getItens().put(OITO_FUROS, new EnfornaForm.Item(linhas8f, null, quantidade8f));
        return f;
    }
    private DesenfornaForm desenforna(Fornada fornada, int linhas, int primeira, int segunda, int cru) {
        var f = new DesenfornaForm(); f.setData(hoje()); f.setChaveEnvio(UUID.randomUUID()); f.setIdFornada(fornada.getId());
        f.getItens().put(OITO_FUROS, new DesenfornaForm.Item(linhas, primeira, segunda, cru));
        return f;
    }
    private Fornada recarregar(Fornada f) { return fornadas.consultar(f.getId(), gestor); }
    private Fornada abrir() { return fornadas.abrirNova(unidade.getId(), gestor); }

    @Test void gestorAbreFornadaNumeradaNoMesEAteDuasEmAndamento() {
        // RN08: without a load opened by the Gestor there is no Enforna, and only the Gestor opens one.
        assertThatThrownBy(() -> enfornas.criar(registro, enforna(null, 3, null))).hasMessageContaining("Nenhuma fornada aberta");
        assertThatThrownBy(() -> fornadas.abrirNova(unidade.getId(), registro)).isInstanceOf(AccessDeniedException.class);
        var primeira = abrir();
        assertThat(primeira.getNumero()).isEqualTo(1);
        assertThat(primeira.getIdentificacao()).startsWith("1ª fornada de ");
        var e = enfornas.criar(registro, enforna(null, 3, null)).getFirst();
        assertThat(e.getFornada().getId()).isEqualTo(primeira.getId());
        assertThat(e.getTijolosPorLinha()).isEqualTo(1610);
        assertThat(e.getQuantidade()).isEqualTo(3 * 1610); // RN12: suggestion lines × reference
        // Opening the next load ends the Enforna of the previous one.
        var segunda = abrir();
        assertThat(segunda.getNumero()).isEqualTo(2);
        assertThat(recarregar(primeira).isRecebendoEnforna()).isFalse();
        assertThatThrownBy(() -> enfornas.criar(registro, enforna(recarregar(primeira), 1, null))).hasMessageContaining("abriu a 2ª fornada");
        assertThat(enfornas.criar(registro, enforna(null, 2, null)).getFirst().getFornada().getId()).isEqualTo(segunda.getId());
        assertThat(fornadas.emAndamento(unidade.getId())).hasSize(2);
        assertThat(fornadas.podeAbrirNova(unidade.getId())).isFalse();
        assertThatThrownBy(this::abrir).isInstanceOf(RegraNegocioException.class);
        // A load with the Registro's records still Pending does not close.
        assertThatThrownBy(() -> fornadas.concluir(primeira.getId(), gestor, recarregar(primeira).getVersao())).hasMessageContaining("pendente");
        validarPendentes(primeira);
        fornadas.concluir(primeira.getId(), gestor, recarregar(primeira).getVersao());
        assertThat(abrir().getNumero()).isEqualTo(3);
    }
    @Test void avisosPedemConfirmacaoELimiteDoFornoBloqueia() {
        var fornada = abrir();
        var f = enforna(null, 2, 5000);
        assertThatThrownBy(() -> enfornas.criar(registro, f)).isInstanceOf(AvisosPendentesException.class)
            .satisfies(e -> assertThat(((AvisosPendentesException) e).getAvisos()).anyMatch(a -> a.contains("2 linhas")));
        assertThat(fornadas.balanco(fornada.getId(), gestor).linhasEnfornadas()).isZero(); // nothing saved
        f.setConfirmarAvisos(true);
        var e = enfornas.criar(registro, f).getFirst();
        assertThat(e.getQuantidade()).isEqualTo(5000);
        assertThat(enfornas.avisos(e)).isNotEmpty(); // the Gestor still sees it when validating
        // RN14: 46 lines, 2 always empty: 44 fit, adding every format, and confirming does not get past it.
        var muitas = enforna(null, 43, null); muitas.setData(hoje().minusDays(1)); muitas.setConfirmarAvisos(true);
        assertThatThrownBy(() -> enfornas.criar(registro, muitas)).isInstanceOf(RegraNegocioException.class).hasMessageContaining("Ainda cabem 42");
        var cheia = enforna(null, 30, null); cheia.setData(hoje().minusDays(1));
        cheia.getItens().put(SEIS_FUROS, new EnfornaForm.Item(12, null, null));
        assertThat(enfornas.criar(registro, cheia)).hasSize(2);
        var mais = enforna(null, 1, null); mais.setData(hoje().minusDays(2));
        assertThatThrownBy(() -> enfornas.criar(registro, mais)).hasMessageContaining("Ainda cabem 0");
        // A correction that does not add lines is accepted even with the kiln full.
        var menos = enforna(null, 1, null); menos.setVersao(e.getVersao()); menos.setIdMotivo(motivo(Motivo.Tipo.CORRECAO, "Digitação errada"));
        menos.setConfirmarAvisos(true);
        assertThat(enfornas.editar(e.getId(), registro, menos).getLinhas()).isEqualTo(1);
    }
    @Test void desenfornaComBalancoEConclusaoComDiferenca() {
        abrir();
        var fornada = enfornas.criar(registro, enforna(null, 3, null)).getFirst().getFornada();
        var d = desenfornas.criar(registro, desenforna(fornada, 2, 2500, 400, 200)).getFirst();
        assertThat(d.getAcabado()).isEqualTo(2900); // RN10: Cru is not finished production
        // RN14: no more lines come out than went in.
        var acima = desenforna(fornada, 2, 2000, 300, 100); acima.setData(hoje().minusDays(1)); acima.setConfirmarAvisos(true);
        assertThatThrownBy(() -> desenfornas.criar(registro, acima)).isInstanceOf(RegraNegocioException.class).hasMessageContaining("Ainda podem sair 1 linhas");
        var resto = desenforna(fornada, 1, 2000, 300, 100); resto.setData(hoje().minusDays(1));
        assertThatThrownBy(() -> desenfornas.criar(registro, resto)).isInstanceOf(AvisosPendentesException.class);
        resto.setConfirmarAvisos(true);
        desenfornas.criar(registro, resto);
        var balanco = fornadas.balanco(fornada.getId(), gestor);
        assertThat(balanco.linhasEnfornadas()).isEqualTo(3);
        assertThat(balanco.linhasDesenfornadas()).isEqualTo(3);
        assertThat(balanco.primeira()).isEqualTo(4500);
        assertThat(balanco.segunda()).isEqualTo(700);
        assertThat(balanco.cru()).isEqualTo(300);
        assertThat(balanco.sugerirConclusao()).isTrue();
        assertThat(balanco.diferenca()).isEqualTo(3100 + 2400 - 4830);
        assertThatThrownBy(() -> fornadas.concluir(fornada.getId(), registro, recarregar(fornada).getVersao())).isInstanceOf(AccessDeniedException.class);
        validarPendentes(fornada);
        fornadas.concluir(fornada.getId(), gestor, recarregar(fornada).getVersao());
        assertThat(recarregar(fornada).isEmAndamento()).isFalse();
        // A concluded load takes no more Desenforna.
        var depois = desenforna(fornada, 1, 1000, 0, 0); depois.setData(hoje().minusDays(2));
        assertThatThrownBy(() -> desenfornas.criar(registro, depois)).isInstanceOf(RegraNegocioException.class);
    }
    @Test void correcaoComMotivoEGestorCorrigeValidado() {
        abrir();
        var e = enfornas.criar(registro, enforna(null, 3, null)).getFirst();
        var semMotivo = enforna(e.getFornada(), 4, null); semMotivo.setVersao(e.getVersao());
        assertThatThrownBy(() -> enfornas.editar(e.getId(), registro, semMotivo)).isInstanceOf(RegraNegocioException.class);
        semMotivo.setIdMotivo(motivo(Motivo.Tipo.CORRECAO, "Outro"));
        assertThatThrownBy(() -> enfornas.editar(e.getId(), registro, semMotivo)).hasMessageContaining("Descreva");
        semMotivo.setTextoMotivo("O encarregado recontou");
        var corrigido = enfornas.editar(e.getId(), registro, semMotivo);
        assertThat(corrigido.getLinhas()).isEqualTo(4);
        var evento = enfornas.historico(e.getId()).getLast();
        assertThat(evento.getMotivo().getDescricao()).isEqualTo("Outro");
        assertThat(evento.getTextoMotivo()).isEqualTo("O encarregado recontou");
        var validado = enfornas.validar(e.getId(), gestor, corrigido.getVersao());
        var deNovo = enforna(e.getFornada(), 5, null); deNovo.setVersao(validado.getVersao());
        deNovo.setIdMotivo(motivo(Motivo.Tipo.CORRECAO, "Digitação errada"));
        assertThatThrownBy(() -> enfornas.editar(e.getId(), registro, deNovo)).isInstanceOf(AccessDeniedException.class); // RN11
        var peloGestor = enfornas.editar(e.getId(), gestor, deNovo);
        assertThat(peloGestor.getSituacao()).isEqualTo(Situacao.VALIDADO);
        assertThat(peloGestor.getLinhas()).isEqualTo(5);
    }
    @Test void cancelamentoComMotivoLiberaANovaDigitacao() {
        abrir();
        var f = enforna(null, 3, null);
        var e = enfornas.criar(registro, f).getFirst();
        assertThatThrownBy(() -> enfornas.cancelar(e.getId(), registro, e.getVersao(), motivo(Motivo.Tipo.CORRECAO, "Contagem errada"), null))
            .isInstanceOf(RegraNegocioException.class); // a correction reason is not a cancellation reason
        var cancelado = enfornas.cancelar(e.getId(), registro, e.getVersao(), motivo(Motivo.Tipo.CANCELAMENTO, "Lançamento duplicado"), null);
        assertThat(cancelado.getSituacao()).isEqualTo(Situacao.CANCELADO);
        assertThat(fornadas.balanco(e.getFornada().getId(), gestor).linhasEnfornadas()).isZero();
        var outra = enforna(e.getFornada(), 2, null); // same load, format and day: allowed again (RN09)
        assertThat(enfornas.criar(registro, outra)).hasSize(1);
        assertThatThrownBy(() -> enfornas.cancelar(e.getId(), gestor, cancelado.getVersao(), motivo(Motivo.Tipo.CANCELAMENTO, "Data errada"), null))
            .isInstanceOf(AccessDeniedException.class); // cancelled is final
    }
    @Test void validacaoEmLoteSoMexeNosPendentes() {
        var m1 = marombaDe(1000, hoje()); var m2 = marombaDe(2000, hoje().minusDays(1)); var m3 = marombaDe(3000, hoje().minusDays(2));
        marombas.validar(m3.getId(), gestor, m3.getVersao());
        assertThatThrownBy(() -> marombas.validarLote(List.of(m1.getId()), registro)).isInstanceOf(AccessDeniedException.class);
        assertThat(marombas.validarLote(List.of(m1.getId(), m2.getId(), m3.getId()), gestor)).isEqualTo(2);
        assertThat(marombas.consultar(m1.getId(), gestor).getSituacao()).isEqualTo(Situacao.VALIDADO);
    }
    private Maromba marombaDe(int quantidade, LocalDate data) {
        var f = new MarombaForm(); f.setData(data); f.setChaveEnvio(UUID.randomUUID());
        f.getQuantidades().put(SEIS_FUROS, 0); f.getQuantidades().put(OITO_FUROS, quantidade);
        return marombas.criar(registro, f).getFirst();
    }
    @Test void inventarioInicialUmaVezEDataMinima() {
        var ref = hoje().minusDays(3);
        var f = new InventarioForm(); f.setIdUnidade(unidade.getId()); f.setReferencia(ref.atTime(18, 0));
        var mes = YearMonth.from(ref);
        f.getItens().add(new InventarioForm.Item(InventarioInicial.Estado.ASSADO, OITO_FUROS, mes, 1, 16, 25760));
        f.getItens().add(new InventarioForm.Item(InventarioInicial.Estado.AGUARDANDO_QUEIMA, SEIS_FUROS, mes, 2, 6, 13200));
        f.getItens().add(new InventarioForm.Item(InventarioInicial.Estado.AGUARDANDO_QUEIMA, OITO_FUROS, mes, 2, 19, 30590));
        f.getItens().add(new InventarioForm.Item(InventarioInicial.Estado.ACABADO_2A, OITO_FUROS, null, null, null, 9100));
        assertThatThrownBy(() -> inventario.registrar(registro, f)).isInstanceOf(AccessDeniedException.class);
        assertThat(inventario.registrar(admin, f)).hasSize(4);
        assertThatThrownBy(() -> inventario.registrar(admin, f)).isInstanceOf(RegraNegocioException.class); // only once (RN20)
        var emAndamento = fornadas.emAndamento(unidade.getId());
        assertThat(emAndamento).extracting(Fornada::getNumero).containsExactly(1, 2);
        assertThat(fornadas.balanco(emAndamento.get(1).getId(), gestor).linhasEnfornadas()).isEqualTo(25);
        // The reference day is already inside the inventory; the next day is accepted.
        assertThatThrownBy(() -> marombaDe(1000, ref)).hasMessageContaining("inventário inicial");
        assertThat(marombaDe(1000, ref.plusDays(1)).getQuantidade()).isEqualTo(1000);
        // Loads from the inventory take Desenforna like any other.
        var d = desenforna(emAndamento.getFirst(), 7, 7700, 1400, 1050); d.setData(ref.plusDays(1));
        assertThat(desenfornas.criar(registro, d)).hasSize(1);
    }
    @Test void horariosDaQueimaEVelocidade() {
        abrir();
        var fornada = enfornas.criar(registro, enforna(null, 40, 64400)).getFirst().getFornada();
        var inicio = Instant.now().minus(Duration.ofDays(5));
        // RN21: the Gestor marks only the esquente and the end; the burning starts about 24 hours after the esquente.
        fornada = fornadas.registrarMarco(fornada.getId(), gestor, fornada.getVersao(), Fornada.Marco.INICIO_ESQUENTE, inicio);
        var semQueima = fornada;
        assertThatThrownBy(() -> fornadas.registrarMarco(semQueima.getId(), gestor, semQueima.getVersao(), Fornada.Marco.INICIO_QUEIMA, inicio))
            .isInstanceOf(RegraNegocioException.class);
        fornada = fornadas.registrarMarco(fornada.getId(), gestor, fornada.getVersao(), Fornada.Marco.FIM_QUEIMA, inicio.plus(Duration.ofHours(104)));
        var balanco = fornadas.balanco(fornada.getId(), geral);
        assertThat(balanco.queimaEstimada()).isTrue();
        assertThat(balanco.horasEsquente()).isEqualTo(24.0);
        assertThat(balanco.horasQueima()).isEqualTo(80.0);
        assertThat(balanco.linhasPor24h()).isEqualTo(12.0);
        var f = fornada;
        assertThatThrownBy(() -> fornadas.registrarMarco(f.getId(), registro, f.getVersao(), Fornada.Marco.FIM_QUEIMA, Instant.now()))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fornadas.registrarMarco(f.getId(), gestor, f.getVersao(), Fornada.Marco.FIM_QUEIMA, Instant.now().plus(Duration.ofDays(1))))
            .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> fornadas.registrarMarco(f.getId(), geral, f.getVersao(), Fornada.Marco.FIM_QUEIMA, Instant.now()))
            .isInstanceOf(AccessDeniedException.class);
    }
    @Autowired EnfornaRepository enfornaRegistros;
    @Autowired DesenfornaRepository desenfornaRegistros;
    private void validarPendentes(Fornada f) {
        for (var e : enfornaRegistros.daFornada(f.getId())) if (e.getSituacao() == Situacao.PENDENTE) enfornaRegistros.save(validado(e));
        for (var d : desenfornaRegistros.daFornada(f.getId())) if (d.getSituacao() == Situacao.PENDENTE) desenfornaRegistros.save(validado(d));
    }
    private <R extends RegistroEtapa> R validado(R r) {
        r.validar(usuarios.findById(gestor.id()).orElseThrow(), java.time.Instant.now());
        return r;
    }
}
