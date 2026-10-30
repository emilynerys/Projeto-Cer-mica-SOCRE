package br.com.socre.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.auditoria.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import static br.com.socre.producao.cadastro.Formato.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FluxoMarombaTest {
    @Autowired ProducaoService service;
    @Autowired MarombaRepository marombas;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeRepository unidades;
    @MockitoSpyBean AuditoriaRepository auditoria;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcIndexedSessionRepository sessoes;
    @Autowired MotivoRepository motivos;
    private UsuarioAutenticado registro, outroRegistro, outraUnidade, gestor, geral, admin;
    private static final String SENHA = "senha-exclusiva-dos-testes";
    private static String senhaHash;

    @BeforeEach void preparar() {
        if (senhaHash == null) senhaHash = encoder.encode(SENHA);
        var unidade = unidades.saveAndFlush(new Unidade(UUID.randomUUID(), "Unidade de teste " + UUID.randomUUID(), 46));
        registro = usuario(Perfil.REGISTRO_PRODUCAO, unidade);
        outroRegistro = usuario(Perfil.REGISTRO_PRODUCAO, unidade);
        outraUnidade = usuario(Perfil.REGISTRO_PRODUCAO, unidades.saveAndFlush(new Unidade(UUID.randomUUID(), "Outra unidade de teste", 46)));
        gestor = usuario(Perfil.GESTOR_PRODUCAO, null);
        geral = usuario(Perfil.GESTOR_GERAL, null);
        admin = usuario(Perfil.ADMINISTRADOR, null);
    }
    private UsuarioAutenticado usuario(Perfil perfil, Unidade unidade) {
        var id = UUID.randomUUID();
        return UsuarioAutenticado.from(usuarios.saveAndFlush(new Usuario(id, perfil.getDescricao(), id + "@teste.local", senhaHash, perfil, unidade)));
    }
    private MarombaForm form(int seis, int oito) {
        var f = new MarombaForm(); f.setData(service.hoje()); f.setChaveEnvio(UUID.randomUUID());
        f.getQuantidades().put(SEIS_FUROS, seis); f.getQuantidades().put(OITO_FUROS, oito);
        return f;
    }
    private UUID motivo(Motivo.Tipo tipo, String descricao) {
        return motivos.findByTipoAndAtivoTrueOrderByDescricao(tipo).stream().filter(m -> m.getDescricao().equals(descricao)).findFirst().orElseThrow().getId();
    }
    // A correction form carries only the format of the record being corrected, and a reason (RN15).
    private MarombaForm correcao(Maromba m, int quantidade) {
        var f = new MarombaForm(); f.setData(m.getData()); f.setChaveEnvio(m.getChaveEnvio()); f.setVersao(m.getVersao());
        f.setIdMotivo(motivo(Motivo.Tipo.CORRECAO, "Contagem errada"));
        f.getQuantidades().put(m.getFormato().getId(), quantidade);
        return f;
    }
    private Maromba doFormato(List<Maromba> registros, UUID formato) {
        return registros.stream().filter(m -> m.getFormato().getId().equals(formato)).findFirst().orElseThrow();
    }
    private List<EventoAuditoria> historico(UUID id) { return auditoria.findByTabelaAndIdRegistroOrderByDataHoraAsc("maromba", id); }
    private RequestPostProcessor autenticado(UsuarioAutenticado p) throws Exception {
        return autenticado(p, Instant.now());
    }
    private RequestPostProcessor autenticado(UsuarioAutenticado p, Instant inicio) throws Exception {
        var resultado = mvc.perform(post("/login").with(csrf()).param("email", p.email()).param("password", SENHA))
            .andExpect(redirectedUrl("/")).andReturn();
        var cookie = resultado.getResponse().getCookie("SOCRE_SESSION");
        assertThat(cookie).isNotNull();
        String id = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        var sessao = sessoes.findById(id);
        assertThat(sessao).isNotNull();
        ((org.springframework.session.Session) sessao).setAttribute(PoliticaSessao.AUTENTICADO_EM, inicio); sessoes.save(sessao);
        return request -> { request.setCookies(cookie); return request; };
    }
    @Test void cadaFormatoComProducaoViraUmRegistro() {
        var criados = service.criar(registro, form(12000, 3000));
        assertThat(criados).hasSize(2).allMatch(m -> m.getSituacao() == Situacao.PENDENTE && m.getOrigem() == Origem.DIGITADO);
        assertThat(doFormato(criados, SEIS_FUROS).getQuantidade()).isEqualTo(12000);
        assertThat(doFormato(criados, OITO_FUROS).getQuantidade()).isEqualTo(3000);
        // Same unit, day and format as an existing record (RN09), even from another author.
        assertThatThrownBy(() -> service.criar(outroRegistro, form(0, 500))).isInstanceOf(RegraNegocioException.class);
        var soOito = service.criar(outraUnidade, form(0, 500));
        assertThat(soOito).singleElement().satisfies(m -> assertThat(m.getFormato().getId()).isEqualTo(OITO_FUROS));
    }
    @Test void fluxoCompletoMantemOriginalEConsideraSomenteValidado() {
        var original = form(12000, 3000);
        var seis = doFormato(service.criar(registro, original), SEIS_FUROS);
        assertThat(marombas.totais(service.hoje(), service.hoje(), registro.unidadeId())).isEmpty();
        seis = service.editar(seis.getId(), gestor, correcao(seis, 13000));
        seis = service.validar(seis.getId(), gestor, seis.getVersao());
        assertThat(seis.getSituacao()).isEqualTo(Situacao.VALIDADO);
        var totais = marombas.totais(service.hoje(), service.hoje(), registro.unidadeId());
        assertThat(totais).hasSize(1);
        assertThat(totais.getFirst().getFormato()).isEqualTo(SEIS_FUROS);
        assertThat(totais.getFirst().getQuantidade()).isEqualTo(13000);
        var historico = historico(seis.getId());
        assertThat(historico).hasSize(3);
        assertThat(historico.getFirst().getDepois()).contains("12000");
        assertThat(historico.get(1).getAntes()).contains("12000");
        assertThat(historico.get(1).getDepois()).contains("13000");
        assertThat(service.criar(registro, original)).extracting(Maromba::getId).contains(seis.getId());
    }
    @Test void reenviarMesmaChaveEhIdempotenteMesmoSimultaneamente() throws Exception {
        var f = form(12000, 0);
        var pronto = new CountDownLatch(2); var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<UUID> envio = () -> { pronto.countDown(); inicio.await(10, TimeUnit.SECONDS); return service.criar(registro, f).getFirst().getId(); };
            var a = pool.submit(envio); var b = pool.submit(envio);
            assertThat(pronto.await(10, TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            var id = a.get(20, TimeUnit.SECONDS);
            assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(historico(id)).hasSize(1);
        }
    }
    @Test void chaveReutilizadaComOutrosValoresNaoAlteraRegistro() {
        var f = form(12000, 0); var m = service.criar(registro, f).getFirst();
        f.getQuantidades().put(SEIS_FUROS, 99);
        assertThatThrownBy(() -> service.criar(registro, f)).isInstanceOf(RegraNegocioException.class);
        assertThat(marombas.findById(m.getId()).orElseThrow().getQuantidade()).isEqualTo(12000);
    }
    @Test void duasChavesSimultaneasNaoDuplicamDiaDaUnidade() throws Exception {
        var pronto = new CountDownLatch(2); var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> envio = () -> {
                pronto.countDown(); inicio.await(10, TimeUnit.SECONDS);
                try { service.criar(registro, form(100, 200)); return true; }
                catch (RegraNegocioException | DataAccessException e) { return false; }
            };
            var a = pool.submit(envio); var b = pool.submit(envio);
            assertThat(pronto.await(10, TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(marombas.consultar(service.hoje(), service.hoje(), registro.id(), registro.unidadeId(), null, PageRequest.of(0, 30)).getTotalElements()).isEqualTo(2);
    }
    @Test void registroNaoVeNemEditaRegistroDeOutroAutorMesmoNaMesmaUnidade() {
        var m = service.criar(registro, form(100, 200)).getFirst();
        for (var outro : List.of(outroRegistro, outraUnidade)) {
            assertThatThrownBy(() -> service.consultar(m.getId(), outro)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.editar(m.getId(), outro, correcao(m, 1))).isInstanceOf(AccessDeniedException.class);
        }
        // RN05: the Administrador reads everything but never changes production records.
        assertThat(service.consultar(m.getId(), admin).getId()).isEqualTo(m.getId());
        assertThatThrownBy(() -> service.editar(m.getId(), admin, correcao(m, 1))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancelar(m.getId(), admin, m.getVersao(), motivo(Motivo.Tipo.CANCELAMENTO, "Data errada"), null)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void geralApenasConsultaEGestorNaoPodeCriarComoRegistroDeProducao() {
        var m = service.criar(registro, form(100, 200)).getFirst();
        assertThat(service.consultar(m.getId(), geral).getId()).isEqualTo(m.getId());
        for (var p : List.of(gestor, geral, admin)) assertThatThrownBy(() -> service.criar(p, form(1, 1))).isInstanceOf(AccessDeniedException.class);
        for (var p : List.of(registro, geral, admin)) assertThatThrownBy(() -> service.validar(m.getId(), p, 0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.editar(m.getId(), geral, correcao(m, 1))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void versaoAntigaNaoSobrescreveAlteracaoEValidadoFicaBloqueado() {
        var m = service.criar(registro, form(100, 200)).getFirst();
        var mudanca = correcao(m, 150);
        var atual = service.editar(m.getId(), registro, mudanca);
        assertThatThrownBy(() -> service.editar(m.getId(), gestor, mudanca)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.validar(m.getId(), gestor, 0)).isInstanceOf(RegraNegocioException.class);
        service.validar(m.getId(), gestor, atual.getVersao());
        assertThatThrownBy(() -> service.editar(m.getId(), registro, mudanca)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void valoresInvalidosNaoPersistemNemGeramAuditoria() {
        var semFormato = form(1, 1); semFormato.getQuantidades().remove(OITO_FUROS);
        for (var f : List.of(form(-1, 1), form(0, 0), form(1000000001, 0), semFormato)) {
            assertThatThrownBy(() -> service.criar(registro, f)).isInstanceOf(RegraNegocioException.class);
            assertThat(marombas.findByChaveEnvioOrderByFormatoNome(f.getChaveEnvio())).isEmpty();
        }
        var futuro = form(1, 0); futuro.setData(service.hoje().plusDays(1));
        assertThatThrownBy(() -> service.criar(registro, futuro)).isInstanceOf(RegraNegocioException.class);
        var m = service.criar(registro, form(100, 0)).getFirst();
        assertThatThrownBy(() -> service.editar(m.getId(), registro, correcao(m, 0))).isInstanceOf(RegraNegocioException.class);
    }
    @Test void bancoImpedeAlterarOuExcluirAuditoriaComCredencialDaAplicacao() {
        var m = service.criar(registro, form(100, 200)).getFirst();
        assertThatThrownBy(() -> jdbc.update("update auditoria set acao='CORRECAO' where id_registro=?", m.getId()))
            .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(java.sql.SQLException.class)
            .satisfies(e -> assertThat(((java.sql.SQLException)e.getCause()).getSQLState()).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("delete from auditoria where id_registro=?", m.getId()))
            .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(java.sql.SQLException.class)
            .satisfies(e -> assertThat(((java.sql.SQLException)e.getCause()).getSQLState()).isEqualTo("42501"));
        assertThat(historico(m.getId())).hasSize(1);
    }
    @Test void falhaNaAuditoriaDesfazLancamentoInteiro() {
        var f = form(100, 200);
        doThrow(new IllegalStateException("Falha simulada na auditoria")).when(auditoria).save(any(EventoAuditoria.class));
        try {
            assertThatThrownBy(() -> service.criar(registro, f)).isInstanceOf(IllegalStateException.class);
            assertThat(marombas.findByChaveEnvioOrderByFormatoNome(f.getChaveEnvio())).isEmpty();
        } finally { reset(auditoria); }
    }
    @Test void loginRealAceitaSenhaCorretaERejeitaIncorreta() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("email", registro.email()).param("password", SENHA))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(post("/login").with(csrf()).param("email", registro.email()).param("password", "errada"))
            .andExpect(redirectedUrl("/login?erro"));
    }
    @Test void rotasExigemLoginPerfilECsrf() throws Exception {
        mvc.perform(get("/producao")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin").with(autenticado(registro))).andExpect(status().isForbidden());
        mvc.perform(get("/painel").with(autenticado(registro))).andExpect(status().isForbidden());
        mvc.perform(get("/producao/maromba").with(autenticado(gestor))).andExpect(status().isForbidden());
        mvc.perform(post("/producao/maromba").with(autenticado(registro))).andExpect(status().isForbidden());
        mvc.perform(get("/producao").with(autenticado(admin))).andExpect(status().isOk());
        mvc.perform(get("/producao/maromba").with(autenticado(admin))).andExpect(status().isForbidden());
    }
    @Test void telasRenderizamComDadosEPermissoes() throws Exception {
        var m = service.criar(registro, form(100, 200)).getFirst();
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Entre na sua conta")));
        mvc.perform(get("/producao").with(autenticado(registro))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("8 furos")));
        mvc.perform(get("/producao/maromba").with(autenticado(registro))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("quantidades[" + SEIS_FUROS + "]")));
        mvc.perform(get("/producao/" + m.getId()).with(autenticado(gestor))).andExpect(status().isOk());
        mvc.perform(get("/producao/" + m.getId() + "/editar").with(autenticado(registro))).andExpect(status().isOk());
        mvc.perform(get("/producao/" + m.getId()).with(autenticado(outroRegistro))).andExpect(status().isForbidden());
        service.validar(m.getId(), gestor, m.getVersao());
        mvc.perform(get("/painel").param("unidade", registro.unidadeId().toString()).with(autenticado(geral))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("6 furos")));
        mvc.perform(get("/admin").with(autenticado(admin))).andExpect(status().isOk());
    }
    @Test void postJsonPreservaReenvioERejeitaFracoes() throws Exception {
        var chave = UUID.randomUUID().toString();
        var url = "/producao/maromba";
        mvc.perform(post(url).with(autenticado(registro)).with(csrf()).accept("application/json")
                .param("data", service.hoje().toString()).param("quantidades[" + SEIS_FUROS + "]", "1.5")
                .param("quantidades[" + OITO_FUROS + "]", "0").param("chaveEnvio", chave))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists());
        for (int i = 0; i < 2; i++) mvc.perform(post(url).with(autenticado(registro)).with(csrf()).accept("application/json")
                .param("data", service.hoje().toString()).param("quantidades[" + SEIS_FUROS + "]", "1500")
                .param("quantidades[" + OITO_FUROS + "]", "0").param("chaveEnvio", chave))
            .andExpect(status().isOk()).andExpect(jsonPath("$.redirect").exists());
        var registros = marombas.findByChaveEnvioOrderByFormatoNome(UUID.fromString(chave));
        assertThat(registros).hasSize(1);
        assertThat(historico(registros.getFirst().getId())).hasSize(1);
    }
    @Test void inativacaoEExpiracaoEncerramSessaoExistente() throws Exception {
        var acesso = autenticado(registro);
        jdbc.update("update usuario set ativo=false where id=?", registro.id());
        mvc.perform(get("/producao").with(acesso)).andExpect(redirectedUrl("/login?expirou"));
        mvc.perform(get("/painel").with(autenticado(gestor, Instant.now().minus(Duration.ofDays(16)))))
            .andExpect(redirectedUrl("/login?expirou"));
    }
}
