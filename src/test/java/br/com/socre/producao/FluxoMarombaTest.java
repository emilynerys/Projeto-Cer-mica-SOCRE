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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FluxoMarombaTest {
    @Autowired ProducaoService service;
    @Autowired LancamentoRepository lancamentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeRepository unidades;
    @MockitoSpyBean AuditoriaRepository auditoria;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcIndexedSessionRepository sessoes;
    private UsuarioAutenticado registro, outroRegistro, outraUnidade, gestor, geral, admin;
    private static final String SENHA = "senha-exclusiva-dos-testes";
    private static String senhaHash;

    @BeforeEach void preparar() {
        if (senhaHash == null) senhaHash = encoder.encode(SENHA);
        var unidade = unidades.saveAndFlush(new Unidade(UUID.randomUUID(), "Unidade de teste " + UUID.randomUUID()));
        registro = usuario(Perfil.REGISTRO_PRODUCAO, unidade);
        outroRegistro = usuario(Perfil.REGISTRO_PRODUCAO, unidade);
        outraUnidade = usuario(Perfil.REGISTRO_PRODUCAO, unidades.saveAndFlush(new Unidade(UUID.randomUUID(), "Outra unidade de teste")));
        gestor = usuario(Perfil.GESTOR_PRODUCAO, null);
        geral = usuario(Perfil.GESTOR_GERAL, null);
        admin = usuario(Perfil.ADMINISTRADOR, null);
    }
    private UsuarioAutenticado usuario(Perfil perfil, Unidade unidade) {
        var id = UUID.randomUUID();
        return UsuarioAutenticado.from(usuarios.saveAndFlush(new Usuario(id, perfil.getDescricao(), id + "@teste.local", senhaHash, perfil, unidade)));
    }
    private MarombaForm form(int seis, int oito) {
        var f = new MarombaForm(); f.setData(service.hoje()); f.setQuantidade6f(seis); f.setQuantidade8f(oito); f.setChaveEnvio(UUID.randomUUID());
        return f;
    }
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
    @Test void fluxoCompletoMantemOriginalEConsideraSomenteValidado() {
        var original = form(12000, 3000);
        var l = service.criar(registro, original);
        assertThat(l.getSituacao()).isEqualTo(Situacao.PENDENTE);
        assertThat(lancamentos.totais(service.hoje(), service.hoje(), registro.unidadeId())).isEmpty();
        var correcao = form(13000, 3000); correcao.setVersao(l.getVersao());
        l = service.editar(l.getId(), gestor, correcao);
        l = service.validar(l.getId(), gestor, l.getVersao());
        assertThat(l.getSituacao()).isEqualTo(Situacao.VALIDADO);
        var totais = lancamentos.totais(service.hoje(), service.hoje(), registro.unidadeId());
        assertThat(totais).hasSize(1);
        assertThat(totais.getFirst().getQuantidade6f()).isEqualTo(13000);
        assertThat(totais.getFirst().getQuantidade8f()).isEqualTo(3000);
        var historico = auditoria.findByLancamentoIdOrderByOcorridoEmAsc(l.getId());
        assertThat(historico).hasSize(3);
        assertThat(historico.getFirst().getValorNovo()).contains("12000");
        assertThat(historico.get(1).getValorAnterior()).contains("12000");
        assertThat(historico.get(1).getValorNovo()).contains("13000");
        assertThat(service.criar(registro, original).getId()).isEqualTo(l.getId());
    }
    @Test void reenviarMesmaChaveEhIdempotenteMesmoSimultaneamente() throws Exception {
        var f = form(12000, 0);
        var pronto = new CountDownLatch(2); var inicio = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<UUID> envio = () -> { pronto.countDown(); inicio.await(10, TimeUnit.SECONDS); return service.criar(registro, f).getId(); };
            var a = pool.submit(envio); var b = pool.submit(envio);
            assertThat(pronto.await(10, TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            var id = a.get(20, TimeUnit.SECONDS);
            assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(auditoria.findByLancamentoIdOrderByOcorridoEmAsc(id)).hasSize(1);
        }
    }
    @Test void chaveReutilizadaComOutrosValoresNaoAlteraRegistro() {
        var f = form(12000, 0); var l = service.criar(registro, f);
        f.setQuantidade6f(99);
        assertThatThrownBy(() -> service.criar(registro, f)).isInstanceOf(RegraNegocioException.class);
        assertThat(lancamentos.findById(l.getId()).orElseThrow().getQuantidade6f()).isEqualTo(12000);
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
        assertThat(lancamentos.consultar(service.hoje(), service.hoje(), registro.id(), registro.unidadeId(), null, PageRequest.of(0, 30)).getTotalElements()).isEqualTo(1);
    }
    @Test void registroNaoVeNemEditaRegistroDeOutroAutorMesmoNaMesmaUnidade() {
        var l = service.criar(registro, form(100, 200));
        for (var outro : List.of(outroRegistro, outraUnidade, admin)) {
            assertThatThrownBy(() -> service.consultar(l.getId(), outro)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.editar(l.getId(), outro, form(1, 1))).isInstanceOf(AccessDeniedException.class);
        }
    }
    @Test void geralApenasConsultaEGestorNaoPodeCriarComoRegistroDeProducao() {
        var l = service.criar(registro, form(100, 200));
        assertThat(service.consultar(l.getId(), geral).getId()).isEqualTo(l.getId());
        for (var p : List.of(gestor, geral, admin)) assertThatThrownBy(() -> service.criar(p, form(1, 1))).isInstanceOf(AccessDeniedException.class);
        for (var p : List.of(registro, geral, admin)) assertThatThrownBy(() -> service.validar(l.getId(), p, 0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.editar(l.getId(), geral, form(1, 1))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void versaoAntigaNaoSobrescreveAlteracaoEValidadoFicaBloqueado() {
        var l = service.criar(registro, form(100, 200));
        var mudanca = form(150, 200); mudanca.setVersao(l.getVersao());
        var atual = service.editar(l.getId(), registro, mudanca);
        assertThatThrownBy(() -> service.editar(l.getId(), gestor, mudanca)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.validar(l.getId(), gestor, 0)).isInstanceOf(RegraNegocioException.class);
        service.validar(l.getId(), gestor, atual.getVersao());
        assertThatThrownBy(() -> service.editar(l.getId(), registro, mudanca)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void valoresInvalidosNaoPersistemNemGeramAuditoria() {
        for (var f : List.of(form(-1, 1), form(0, 0), form(1000000001, 0))) {
            assertThatThrownBy(() -> service.criar(registro, f)).isInstanceOf(RegraNegocioException.class);
            assertThat(lancamentos.findByChaveEnvio(f.getChaveEnvio())).isEmpty();
        }
        var futuro = form(1, 0); futuro.setData(service.hoje().plusDays(1));
        assertThatThrownBy(() -> service.criar(registro, futuro)).isInstanceOf(RegraNegocioException.class);
    }
    @Test void bancoImpedeAlterarOuExcluirAuditoriaComCredencialDaAplicacao() {
        var l = service.criar(registro, form(100, 200));
        assertThatThrownBy(() -> jdbc.update("update auditoria set acao='EDICAO' where lancamento_id=?", l.getId()))
            .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(java.sql.SQLException.class)
            .satisfies(e -> assertThat(((java.sql.SQLException)e.getCause()).getSQLState()).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("delete from auditoria where lancamento_id=?", l.getId()))
            .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(java.sql.SQLException.class)
            .satisfies(e -> assertThat(((java.sql.SQLException)e.getCause()).getSQLState()).isEqualTo("42501"));
        assertThat(auditoria.findByLancamentoIdOrderByOcorridoEmAsc(l.getId())).hasSize(1);
    }
    @Test void falhaNaAuditoriaDesfazLancamentoInteiro() {
        var f = form(100, 200);
        doThrow(new IllegalStateException("Falha simulada na auditoria")).when(auditoria).save(any(EventoAuditoria.class));
        try {
            assertThatThrownBy(() -> service.criar(registro, f)).isInstanceOf(IllegalStateException.class);
            assertThat(lancamentos.findByChaveEnvio(f.getChaveEnvio())).isEmpty();
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
        mvc.perform(get("/producao").with(autenticado(admin))).andExpect(status().isForbidden());
    }
    @Test void telasRenderizamComDadosEPermissoes() throws Exception {
        var l = service.criar(registro, form(100, 200));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Entre na sua conta")));
        mvc.perform(get("/producao").with(autenticado(registro))).andExpect(status().isOk());
        mvc.perform(get("/producao/maromba").with(autenticado(registro))).andExpect(status().isOk());
        mvc.perform(get("/producao/" + l.getId()).with(autenticado(gestor))).andExpect(status().isOk());
        mvc.perform(get("/producao/" + l.getId() + "/editar").with(autenticado(registro))).andExpect(status().isOk());
        mvc.perform(get("/producao/" + l.getId()).with(autenticado(outroRegistro))).andExpect(status().isForbidden());
        service.validar(l.getId(), gestor, l.getVersao());
        mvc.perform(get("/painel").param("unidade", registro.unidadeId().toString()).with(autenticado(geral))).andExpect(status().isOk());
        mvc.perform(get("/admin").with(autenticado(admin))).andExpect(status().isOk());
    }
    @Test void postJsonPreservaReenvioERejeitaFracoes() throws Exception {
        var chave = UUID.randomUUID().toString();
        var url = "/producao/maromba";
        mvc.perform(post(url).with(autenticado(registro)).with(csrf()).accept("application/json")
                .param("data", service.hoje().toString()).param("quantidade6f", "1.5").param("quantidade8f", "0").param("chaveEnvio", chave))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists());
        for (int i = 0; i < 2; i++) mvc.perform(post(url).with(autenticado(registro)).with(csrf()).accept("application/json")
                .param("data", service.hoje().toString()).param("quantidade6f", "1500").param("quantidade8f", "0").param("chaveEnvio", chave))
            .andExpect(status().isOk()).andExpect(jsonPath("$.redirect").exists());
        var l = lancamentos.findByChaveEnvio(UUID.fromString(chave)).orElseThrow();
        assertThat(auditoria.findByLancamentoIdOrderByOcorridoEmAsc(l.getId())).hasSize(1);
    }
    @Test void inativacaoEExpiracaoEncerramSessaoExistente() throws Exception {
        var acesso = autenticado(registro);
        jdbc.update("update usuario set ativo=false where id=?", registro.id());
        mvc.perform(get("/producao").with(acesso)).andExpect(redirectedUrl("/login?expirou"));
        mvc.perform(get("/painel").with(autenticado(gestor, Instant.now().minus(Duration.ofDays(8)))))
            .andExpect(redirectedUrl("/login?expirou"));
    }
}
