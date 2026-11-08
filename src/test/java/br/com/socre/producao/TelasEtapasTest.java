package br.com.socre.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.producao.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static br.com.socre.producao.cadastro.Formato.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Enforna, Desenforna, unified list and Fornadas screens, through HTTP like the browser does.
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class TelasEtapasTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeRepository unidades;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcIndexedSessionRepository sessoes;
    @Autowired EnfornaRepository enfornas;
    @Autowired DesenfornaRepository desenfornas;
    @Autowired FornadaService fornadas;
    @Autowired ProducaoService producao;
    @Autowired StatusFornoRepository statusForno;
    private UsuarioAutenticado registro, gestor, admin;
    private Unidade unidade;
    private static final String SENHA = "senha-exclusiva-dos-testes";
    private static String senhaHash;

    @BeforeEach void preparar() {
        if (senhaHash == null) senhaHash = encoder.encode(SENHA);
        unidade = unidades.saveAndFlush(new Unidade(UUID.randomUUID(), "Unidade de teste " + UUID.randomUUID(), 46));
        registro = usuario(Perfil.REGISTRO_PRODUCAO, unidade);
        gestor = usuario(Perfil.GESTOR_PRODUCAO, null);
        admin = usuario(Perfil.ADMINISTRADOR, null);
    }
    private UsuarioAutenticado usuario(Perfil perfil, Unidade u) {
        var id = UUID.randomUUID();
        return UsuarioAutenticado.from(usuarios.saveAndFlush(new Usuario(id, perfil.getDescricao(), id + "@teste.local", senhaHash, perfil, u)));
    }
    private RequestPostProcessor autenticado(UsuarioAutenticado p) throws Exception { return autenticado(p, SENHA); }
    private RequestPostProcessor autenticado(UsuarioAutenticado p, String senha) throws Exception {
        var resultado = mvc.perform(post("/login").with(csrf()).param("login", p.login()).param("password", senha))
            .andExpect(redirectedUrl("/")).andReturn();
        var cookie = resultado.getResponse().getCookie("SOCRE_SESSION");
        String id = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        var sessao = sessoes.findById(id);
        ((org.springframework.session.Session) sessao).setAttribute(PoliticaSessao.AUTENTICADO_EM, Instant.now()); sessoes.save(sessao);
        return request -> { request.setCookies(cookie); return request; };
    }
    /** Posts a new Enforna of 8 furos only, in the open load, and returns the created record. */
    private Enforna registrarEnforna(RequestPostProcessor sessao, int linhas) throws Exception {
        // 6 furos left all zero, bricks per line included: a day with only one format.
        var url = mvc.perform(post("/producao/enforna/nova").with(sessao).with(csrf())
                .param("data", producao.hoje().toString()).param("chaveEnvio", UUID.randomUUID().toString()).param("versao", "0").param("idFornada", "")
                .param("itens[" + SEIS_FUROS + "].linhas", "0").param("itens[" + SEIS_FUROS + "].tijolosPorLinha", "0").param("itens[" + SEIS_FUROS + "].quantidade", "0")
                .param("itens[" + OITO_FUROS + "].linhas", String.valueOf(linhas)).param("itens[" + OITO_FUROS + "].tijolosPorLinha", "1610")
                .param("itens[" + OITO_FUROS + "].quantidade", String.valueOf(linhas * 1610)))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(url).startsWith("/producao/enforna/");
        return enfornas.findById(UUID.fromString(url.substring(url.lastIndexOf('/') + 1))).orElseThrow();
    }

    @Test void enfornaPelaTelaNaFornadaQueOGestorAbriu() throws Exception {
        var sessao = autenticado(registro);
        // The Registro de Produção starts on the three stages; the history has its own page.
        mvc.perform(get("/").with(sessao)).andExpect(redirectedUrl("/lancamentos"));
        mvc.perform(get("/lancamentos").with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString("/producao/enforna/nova"))).andExpect(content().string(containsString("Histórico de registros")));
        mvc.perform(get("/producao/enforna/nova").with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Nenhuma fornada aberta"))).andExpect(content().string(containsString("Tijolos por linha")));
        var sessaoGestor = autenticado(gestor);
        mvc.perform(get("/lancamentos").with(sessaoGestor)).andExpect(status().isOk()).andExpect(content().string(containsString("Situação do forno agora")));
        mvc.perform(post("/fornadas/abrir").with(sessao).with(csrf()).param("unidade", unidade.getId().toString())).andExpect(status().isForbidden());
        mvc.perform(post("/fornadas/abrir").with(sessaoGestor).with(csrf()).param("unidade", unidade.getId().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("sucesso", org.hamcrest.Matchers.containsString("aberta")));
        mvc.perform(get("/producao/enforna/nova").with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString("1ª fornada de"))).andExpect(content().string(containsString("0 de 44 linhas ocupadas")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Nenhuma fornada aberta"))));
        var e = registrarEnforna(sessao, 10);
        assertThat(e.getQuantidade()).isEqualTo(16100);
        assertThat(e.getFornada().getNumero()).isEqualTo(1);
        mvc.perform(get("/producao/enforna/" + e.getId()).with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString(e.getFornada().getIdentificacao()))).andExpect(content().string(containsString("16.100 tijolos")));
        mvc.perform(get("/producao/enforna/nova").with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString("10 de 44 linhas ocupadas"))).andExpect(content().string(containsString("cabem mais 34")));
    }

    @Test void desenfornaPelaTelaEListaPorDiaComValidacaoEmLote() throws Exception {
        var sessaoRegistro = autenticado(registro);
        fornadas.abrirNova(unidade.getId(), gestor);
        var e = registrarEnforna(sessaoRegistro, 4);
        mvc.perform(get("/producao/desenforna/nova").with(sessaoRegistro)).andExpect(status().isOk()).andExpect(content().string(containsString("1ª qualidade")));
        mvc.perform(post("/producao/desenforna/nova").with(sessaoRegistro).with(csrf())
                .param("data", producao.hoje().toString()).param("chaveEnvio", UUID.randomUUID().toString()).param("versao", "0").param("idFornada", e.getFornada().getId().toString())
                .param("itens[" + SEIS_FUROS + "].linhas", "0").param("itens[" + SEIS_FUROS + "].primeira", "0").param("itens[" + SEIS_FUROS + "].segunda", "0").param("itens[" + SEIS_FUROS + "].cru", "0")
                .param("itens[" + OITO_FUROS + "].linhas", "4").param("itens[" + OITO_FUROS + "].primeira", "6000").param("itens[" + OITO_FUROS + "].segunda", "400").param("itens[" + OITO_FUROS + "].cru", "40"))
            .andExpect(status().is3xxRedirection());
        var d = desenfornas.daFornada(e.getFornada().getId()).getFirst();
        mvc.perform(get("/producao").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Histórico de registros")))
            .andExpect(content().string(containsString("Enforna"))).andExpect(content().string(containsString("Desenforna")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Validar selecionados"))));
        // "Todas" in the filters arrives as empty values and must not hide the records.
        mvc.perform(get("/producao").param("inicio", producao.hoje().minusDays(30).toString()).param("fim", producao.hoje().toString())
                .param("etapa", "").param("situacao", "").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString("2 registro(s)"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Nenhum registro neste período"))));

        var sessaoGestor = autenticado(gestor);
        // One line per day; the day's records, with the boxes to validate, are in its window.
        mvc.perform(get("/producao").with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Dias com lançamento"))).andExpect(content().string(containsString("1 Enforna · 1 Desenforna")))
            .andExpect(content().string(containsString("Ver lançamentos"))).andExpect(content().string(containsString("<dialog")))
            .andExpect(content().string(containsString("Validar selecionados"))).andExpect(content().string(containsString("desenforna:" + d.getId())));
        mvc.perform(post("/producao/validar-lote").with(sessaoGestor).with(csrf()).param("ids", "enforna:" + e.getId(), "desenforna:" + d.getId()))
            .andExpect(redirectedUrl("/producao")).andExpect(flash().attribute("sucesso", "2 registro(s) validado(s)."));
        assertThat(enfornas.findById(e.getId()).orElseThrow().getSituacao()).isEqualTo(Situacao.VALIDADO);
        assertThat(desenfornas.findById(d.getId()).orElseThrow().getSituacao()).isEqualTo(Situacao.VALIDADO);
        mvc.perform(post("/producao/validar-lote").with(sessaoRegistro).with(csrf()).param("ids", "enforna:" + e.getId())).andExpect(status().isForbidden());
        // The panel shows the validated stages: 4 lines × 1.610 in, 6.000 + 400 finished out.
        mvc.perform(get("/painel").param("unidade", unidade.getId().toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Forno no período"))).andExpect(content().string(containsString("6,44")))
            .andExpect(content().string(containsString("6,4"))).andExpect(content().string(containsString("93% do que saiu do forno")))
            .andExpect(content().string(containsString("6% do que saiu do forno"))).andExpect(content().string(containsString("1% · fora da produção acabada")))
            .andExpect(content().string(containsString("Últimas fornadas"))).andExpect(content().string(containsString(e.getFornada().getIdentificacao())));
    }

    @Test void inventarioInicialPelaTelaUmaVezSo() throws Exception {
        var sessaoAdmin = autenticado(admin);
        var url = "/admin/inventario";
        mvc.perform(get(url).param("unidade", unidade.getId().toString()).with(sessaoAdmin)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Salvar inventário inicial")));
        var ontem = producao.hoje().minusDays(1).atTime(18, 0).toString();
        var mes = java.time.YearMonth.from(producao.hoje()).toString();
        mvc.perform(post(url).with(sessaoAdmin).with(csrf()).param("idUnidade", unidade.getId().toString()).param("referencia", ontem)
                .param("cargas[0].mes", mes).param("cargas[0].numero", "1").param("cargas[0].estado", "ASSADO")
                .param("cargas[0].itens[" + OITO_FUROS + "].linhas", "12").param("cargas[0].itens[" + OITO_FUROS + "].quantidade", "19320")
                .param("cargas[0].itens[" + SEIS_FUROS + "].linhas", "").param("cargas[0].itens[" + SEIS_FUROS + "].quantidade", "")
                .param("cargas[1].mes", "").param("cargas[1].numero", "").param("cargas[1].estado", "AGUARDANDO_QUEIMA")
                .param("primeira[" + OITO_FUROS + "]", "5000").param("segunda[" + SEIS_FUROS + "]", "300"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("sucesso", org.hamcrest.Matchers.startsWith("Inventário inicial registrado: 3 item(ns).")));
        mvc.perform(get(url).param("unidade", unidade.getId().toString()).with(sessaoAdmin)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Inventário registrado"))).andExpect(content().string(containsString("Assado (já queimado)")))
            .andExpect(content().string(containsString("19.320"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Salvar inventário inicial"))));
        // Informed only once (RN20).
        mvc.perform(post(url).with(sessaoAdmin).with(csrf()).param("idUnidade", unidade.getId().toString()).param("referencia", ontem)
                .param("primeira[" + OITO_FUROS + "]", "1"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("já foi informado")));
        mvc.perform(get(url).with(autenticado(registro))).andExpect(status().isForbidden());
    }

    @Test void fornadasMostraBalancoEOGestorMarcaHorarios() throws Exception {
        var sessaoRegistro = autenticado(registro);
        fornadas.abrirNova(unidade.getId(), gestor);
        var e = registrarEnforna(sessaoRegistro, 6);
        var f = e.getFornada();
        // The Registro de Produção only reads: no firing times, no opening or closing.
        mvc.perform(get("/fornadas").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString(f.getIdentificacao()))).andExpect(content().string(containsString("1ª qualidade")))
            .andExpect(content().string(containsString("Início do esquente"))).andExpect(content().string(containsString("Não marcado")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">Marcar<"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Abrir nova fornada"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Fechar esta fornada"))));
        mvc.perform(post("/fornadas/" + f.getId() + "/marco").with(sessaoRegistro).with(csrf()).param("versao", String.valueOf(f.getVersao())).param("marco", "INICIO_ESQUENTE"))
            .andExpect(status().isForbidden());

        var sessaoGestor = autenticado(gestor);
        mvc.perform(get("/fornadas").param("unidade", unidade.getId().toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString(">Marcar<"))).andExpect(content().string(containsString("Abrir nova fornada")))
            .andExpect(content().string(containsString("Fechar esta fornada")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Início da queima<"))));
        mvc.perform(post("/fornadas/" + f.getId() + "/marco").with(sessaoGestor).with(csrf()).param("versao", String.valueOf(f.getVersao())).param("marco", "INICIO_ESQUENTE"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("sucesso"));
        assertThat(fornadas.consultar(f.getId(), gestor).getInicioEsquente()).isNotNull();
        mvc.perform(get("/fornadas").param("unidade", unidade.getId().toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Início da queima (estimado"))).andExpect(content().string(containsString(">Corrigir<")));

        var sessaoAdmin = autenticado(admin);
        mvc.perform(get("/fornadas").param("unidade", unidade.getId().toString()).with(sessaoAdmin)).andExpect(status().isOk())
            .andExpect(content().string(containsString(f.getIdentificacao()))).andExpect(content().string(org.hamcrest.Matchers.not(containsString(">Corrigir<"))));
        mvc.perform(post("/fornadas/" + f.getId() + "/concluir").with(sessaoAdmin).with(csrf()).param("versao", "1")).andExpect(status().isForbidden());
        mvc.perform(get("/producao/enforna/nova").with(sessaoGestor)).andExpect(status().isOk());
    }

    @Test void statusDoFornoLancadoPeloGestorComResumoEVistoPorTodos() throws Exception {
        var sessaoRegistro = autenticado(registro);
        mvc.perform(get("/status-forno").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Nenhum status lançado ainda")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Lançar status"))));
        mvc.perform(get("/status-forno/novo").with(sessaoRegistro)).andExpect(status().isForbidden());
        mvc.perform(post("/status-forno").with(sessaoRegistro).with(csrf()).param("idUnidade", unidade.getId().toString())).andExpect(status().isForbidden());

        var sessaoGestor = autenticado(gestor);
        var quando = java.time.LocalDateTime.now(RegistroApoio.FUSO).minusMinutes(5).withSecond(0).withNano(0).toString();
        // A brick line without a state is refused, and the screen comes back with the message.
        mvc.perform(post("/status-forno").with(sessaoGestor).with(csrf()).param("idUnidade", unidade.getId().toString()).param("dataHora", quando)
                .param("linhas[5].idFormato", OITO_FUROS.toString()).param("linhas[5].estado", ""))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Linha 5: escolha o status do tijolo.")));
        // The factory's own example: lines 2 to 37 of 8 furos in Esquente are 36 × 1.610 = 57.960 bricks; line 38 is burning.
        var envio = post("/status-forno").with(sessaoGestor).with(csrf()).param("idUnidade", unidade.getId().toString()).param("dataHora", quando)
            .param("observacao", "Esquente iniciado às 12h de domingo");
        for (int n = 2; n <= 37; n++) envio.param("linhas[" + n + "].idFormato", OITO_FUROS.toString()).param("linhas[" + n + "].estado", "ESQUENTE");
        envio.param("linhas[38].idFormato", OITO_FUROS.toString()).param("linhas[38].estado", "QUEIMA");
        mvc.perform(envio).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("sucesso"));

        mvc.perform(get("/status-forno").param("unidade", unidade.getId().toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Esquente iniciado às 12h de domingo")))
            .andExpect(content().string(containsString("57.960"))).andExpect(content().string(containsString("59.570")))
            .andExpect(content().string(containsString("<span>9</span> <span>linhas vazias</span>")))
            .andExpect(content().string(containsString("data-estado=\"QUEIMA\"")))
            .andExpect(content().string(containsString("Lançar status")));
        mvc.perform(get("/status-forno/novo").param("unidade", unidade.getId().toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Preencher várias linhas"))).andExpect(content().string(containsString("data-fogo-aqui=\"38\"")))
            .andExpect(content().string(containsString("name=\"linhas[46].estado\""))).andExpect(content().string(containsString("name=\"linhas[38].quantidade\" value=\"1610\"")));
        // Registro de Produção reads the map of its unit but does not launch it.
        mvc.perform(get("/status-forno").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString("57.960"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Lançar status"))));
        var sessaoAdmin = autenticado(admin);
        mvc.perform(get("/status-forno").param("unidade", unidade.getId().toString()).with(sessaoAdmin)).andExpect(status().isOk())
            .andExpect(content().string(containsString("57.960"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Lançar status"))));
        mvc.perform(get("/status-forno/novo").with(sessaoAdmin)).andExpect(status().isForbidden());
        // The Gestor Geral reads the map but does not launch it (08/11/2026).
        var sessaoGeral = autenticado(usuario(Perfil.GESTOR_GERAL, null));
        mvc.perform(get("/status-forno/novo").param("unidade", unidade.getId().toString()).with(sessaoGeral)).andExpect(status().isForbidden());
        mvc.perform(get("/status-forno").param("unidade", unidade.getId().toString()).with(sessaoGeral)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Lançar status"))));
    }

    /** Posts a kiln map with the given format in Assado on lines 2 onward, the rest empty. */
    private void lancarStatus(RequestPostProcessor sessao, UUID formato, int linhas, int minutosAtras) throws Exception {
        var quando = java.time.LocalDateTime.now(RegistroApoio.FUSO).minusMinutes(minutosAtras).withSecond(0).withNano(0).toString();
        var envio = post("/status-forno").with(sessao).with(csrf()).param("idUnidade", unidade.getId().toString()).param("dataHora", quando);
        for (int n = 2; n < 2 + linhas; n++) envio.param("linhas[" + n + "].idFormato", formato.toString()).param("linhas[" + n + "].estado", "ASSADO");
        mvc.perform(envio).andExpect(status().is3xxRedirection());
    }
    @Test void statusDoFornoConfereOMapaComAsFornadasEmAndamento() throws Exception {
        var sessaoGestor = autenticado(gestor); var sessaoRegistro = autenticado(registro);
        var tela = get("/status-forno").param("unidade", unidade.getId().toString());
        // Without a load in progress there is nothing to compare.
        lancarStatus(sessaoGestor, OITO_FUROS, 10, 20);
        mvc.perform(tela.with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Nenhuma fornada em andamento para comparar")));

        fornadas.abrirNova(unidade.getId(), gestor);
        registrarEnforna(sessaoRegistro, 10);
        // Same lines and same format on both sides, and every profile sees it.
        mvc.perform(tela.with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString("dizem 10 linhas no forno. O mapa mostra 10. Está batendo.")))
            .andExpect(content().string(containsString("(recebendo Enforna)")));

        lancarStatus(sessaoGestor, OITO_FUROS, 8, 15);
        mvc.perform(tela.with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("dizem 10 linhas no forno, mas o mapa mostra 8.")))
            .andExpect(content().string(containsString("8F: 10 pelas fornadas, 8 no mapa")));

        // The total matches, but the map has another format.
        lancarStatus(sessaoGestor, SEIS_FUROS, 10, 10);
        mvc.perform(tela.with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("O total de 10 linhas bate, mas os formatos não.")))
            .andExpect(content().string(containsString("6F: 0 pelas fornadas, 10 no mapa")));

        // Desenforna takes lines out of the expected count.
        var fornada = fornadas.emAndamento(unidade.getId()).getFirst();
        mvc.perform(post("/producao/desenforna/nova").with(sessaoRegistro).with(csrf())
                .param("data", producao.hoje().toString()).param("chaveEnvio", UUID.randomUUID().toString()).param("versao", "0").param("idFornada", fornada.getId().toString())
                .param("itens[" + SEIS_FUROS + "].linhas", "0").param("itens[" + SEIS_FUROS + "].primeira", "0").param("itens[" + SEIS_FUROS + "].segunda", "0").param("itens[" + SEIS_FUROS + "].cru", "0")
                .param("itens[" + OITO_FUROS + "].linhas", "3").param("itens[" + OITO_FUROS + "].primeira", "4500").param("itens[" + OITO_FUROS + "].segunda", "300").param("itens[" + OITO_FUROS + "].cru", "30"))
            .andExpect(status().is3xxRedirection());
        lancarStatus(sessaoGestor, OITO_FUROS, 7, 5);
        mvc.perform(tela.with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("dizem 7 linhas no forno. O mapa mostra 7. Está batendo.")));

        // An older picture is not checked against today's loads.
        var antigo = statusForno.findByUnidadeIdOrderByDataHoraDescCriadoEmDesc(unidade.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getLast();
        mvc.perform(get("/status-forno").param("id", antigo.getId().toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-conferencia"))));
    }

    @Test void indicadoresExportadosEmPdfComOsMesmosFiltros() throws Exception {
        var sessaoRegistro = autenticado(registro); var sessaoGestor = autenticado(gestor);
        fornadas.abrirNova(unidade.getId(), gestor);
        var e = registrarEnforna(sessaoRegistro, 4);
        mvc.perform(post("/producao/desenforna/nova").with(sessaoRegistro).with(csrf())
                .param("data", producao.hoje().toString()).param("chaveEnvio", UUID.randomUUID().toString()).param("versao", "0").param("idFornada", e.getFornada().getId().toString())
                .param("itens[" + SEIS_FUROS + "].linhas", "0").param("itens[" + SEIS_FUROS + "].primeira", "0").param("itens[" + SEIS_FUROS + "].segunda", "0").param("itens[" + SEIS_FUROS + "].cru", "0")
                .param("itens[" + OITO_FUROS + "].linhas", "4").param("itens[" + OITO_FUROS + "].primeira", "6000").param("itens[" + OITO_FUROS + "].segunda", "400").param("itens[" + OITO_FUROS + "].cru", "40"))
            .andExpect(status().is3xxRedirection());
        var d = desenfornas.daFornada(e.getFornada().getId()).getFirst();
        mvc.perform(post("/producao/validar-lote").with(sessaoGestor).with(csrf()).param("ids", "enforna:" + e.getId(), "desenforna:" + d.getId())).andExpect(status().is3xxRedirection());

        var hoje = producao.hoje().toString();
        mvc.perform(get("/painel").param("unidade", unidade.getId().toString()).param("inicio", hoje).param("fim", hoje).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Exportar PDF")))
            .andExpect(content().string(containsString("/painel/relatorio.pdf?inicio=" + hoje + "&amp;fim=" + hoje + "&amp;unidade=" + unidade.getId())));
        var pdf = mvc.perform(get("/painel/relatorio.pdf").param("unidade", unidade.getId().toString()).param("inicio", hoje).param("fim", hoje).with(sessaoGestor))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"indicadores-" + hoje + "-a-" + hoje + ".pdf\""))
            .andReturn().getResponse().getContentAsByteArray();
        var texto = new com.lowagie.text.pdf.parser.PdfTextExtractor(new com.lowagie.text.pdf.PdfReader(pdf)).getTextFromPage(1);
        assertThat(texto).contains(unidade.getNome()).contains("Etapas por formato").contains("6,44").contains("93%").contains(e.getFornada().getIdentificacao());

        // Without a unit it covers all of them, and an empty "Todas as unidades" arrives like the filter sends it.
        var todas = mvc.perform(get("/painel/relatorio.pdf").param("unidade", "").param("inicio", hoje).param("fim", hoje).with(autenticado(usuario(Perfil.GESTOR_GERAL, null))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new com.lowagie.text.pdf.parser.PdfTextExtractor(new com.lowagie.text.pdf.PdfReader(todas)).getTextFromPage(1)).contains("Todas as unidades");

        // Same access as the screen, and the same period rule.
        mvc.perform(get("/painel/relatorio.pdf").with(sessaoRegistro)).andExpect(status().isForbidden());
        mvc.perform(get("/painel/relatorio.pdf").with(autenticado(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/painel/relatorio.pdf").param("inicio", hoje).param("fim", java.time.LocalDate.parse(hoje).minusDays(1).toString()).with(sessaoGestor)).andExpect(status().isBadRequest());
        mvc.perform(get("/painel/relatorio.pdf").param("unidade", UUID.randomUUID().toString()).with(sessaoGestor)).andExpect(status().isNotFound());
    }

    @Test void administradorCadastraUsuarioRedefineSenhaEDesativa() throws Exception {
        var sessaoAdmin = autenticado(admin);
        var email = "pessoa" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(get("/admin").with(sessaoAdmin)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Novo usuário"))).andExpect(content().string(containsString("Novo formato"))).andExpect(content().string(containsString("Adicionar motivo")));
        mvc.perform(get("/admin/usuarios/novo").with(autenticado(gestor))).andExpect(status().isForbidden());
        // Short password and repeated e-mail come back with the message, and nothing is saved.
        mvc.perform(post("/admin/usuarios").with(sessaoAdmin).with(csrf()).param("nome", "Pessoa nova").param("login", email).param("perfil", "GESTOR_GERAL").param("senha", "curta"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("ao menos 8 caracteres")));
        mvc.perform(post("/admin/usuarios").with(sessaoAdmin).with(csrf()).param("nome", "Com e-mail").param("login", "nome@exemplo.com").param("perfil", "GESTOR_GERAL").param("senha", SENHA))
            .andExpect(status().isOk()).andExpect(content().string(containsString("sem espaço nem acento")));
        mvc.perform(post("/admin/usuarios").with(sessaoAdmin).with(csrf()).param("nome", "Pessoa nova").param("login", email).param("perfil", "GESTOR_GERAL").param("senha", SENHA))
            .andExpect(redirectedUrl("/admin"));
        mvc.perform(post("/admin/usuarios").with(sessaoAdmin).with(csrf()).param("nome", "Repetida").param("login", email).param("perfil", "GESTOR_GERAL").param("senha", SENHA))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Já existe uma conta")));
        var novo = usuarios.findByLoginIgnoreCase(email).orElseThrow();
        assertThat(novo.getSenhaHash()).isNotEqualTo(SENHA);
        var sessaoNova = autenticado(UsuarioAutenticado.from(novo));
        // First access: the provisional password must be changed before anything else.
        mvc.perform(get("/painel").with(sessaoNova)).andExpect(redirectedUrl("/senha"));
        mvc.perform(post("/senha").with(sessaoNova).with(csrf()).param("atual", SENHA).param("nova", "curta").param("confirmacao", "curta"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("ao menos 8 caracteres")));
        mvc.perform(post("/senha").with(sessaoNova).with(csrf()).param("atual", SENHA).param("nova", "senha-da-pessoa").param("confirmacao", "senha-da-pessoa"))
            .andExpect(redirectedUrl("/login?senhaTrocada"));
        assertThat(usuarios.findById(novo.getId()).orElseThrow().isTrocarSenha()).isFalse();
        sessaoNova = autenticado(UsuarioAutenticado.from(usuarios.findById(novo.getId()).orElseThrow()), "senha-da-pessoa");
        mvc.perform(get("/painel").with(sessaoNova)).andExpect(status().isOk());
        // Reset: the old session ends, the old password stops working, the new one works, and the trail does not hold it.
        var senhaNova = "outra-senha-do-teste";
        mvc.perform(post("/admin/usuarios/" + novo.getId() + "/senha").with(sessaoAdmin).with(csrf()).param("senha", senhaNova)).andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/painel").with(sessaoNova)).andExpect(redirectedUrl("/login?expirou"));
        mvc.perform(post("/login").with(csrf()).param("login", email).param("password", SENHA)).andExpect(redirectedUrl("/login?erro"));
        mvc.perform(post("/login").with(csrf()).param("login", email).param("password", senhaNova)).andExpect(redirectedUrl("/"));
        // Deactivated accounts do not enter; the Administrador cannot lock itself out.
        mvc.perform(post("/admin/usuarios/" + novo.getId()).with(sessaoAdmin).with(csrf()).param("nome", "Pessoa nova").param("login", email).param("perfil", "GESTOR_GERAL")).andExpect(redirectedUrl("/admin"));
        assertThat(usuarios.findById(novo.getId()).orElseThrow().isAtivo()).isFalse();
        mvc.perform(post("/login").with(csrf()).param("login", email).param("password", senhaNova)).andExpect(redirectedUrl("/login?erro"));
        mvc.perform(post("/admin/usuarios/" + admin.id()).with(sessaoAdmin).with(csrf()).param("nome", "Eu").param("login", admin.login()).param("perfil", "ADMINISTRADOR"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("própria conta")));
    }
    @Test void administradorAlteraFormatoCapacidadeEMotivos() throws Exception {
        var sessaoAdmin = autenticado(admin);
        var nome = "Formato " + UUID.randomUUID();
        mvc.perform(post("/admin/formatos").with(sessaoAdmin).with(csrf()).param("nome", nome).param("tijolosPorLinha", "0"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("quantos tijolos")));
        mvc.perform(post("/admin/formatos").with(sessaoAdmin).with(csrf()).param("nome", nome).param("tijolosPorLinha", "1000")).andExpect(redirectedUrl("/admin"));
        var f = producao.formatosAtivos().stream().filter(x -> x.getNome().equals(nome)).findFirst().orElseThrow();
        assertThat(f.getTijolosPorLinha()).isEqualTo(1000);
        // Taking it out of use removes it from the forms.
        mvc.perform(post("/admin/formatos/" + f.getId()).with(sessaoAdmin).with(csrf()).param("nome", nome).param("tijolosPorLinha", "1200")).andExpect(redirectedUrl("/admin"));
        assertThat(producao.formatosAtivos()).noneMatch(x -> x.getNome().equals(nome));

        mvc.perform(post("/admin/unidades/" + unidade.getId() + "/forno").with(sessaoAdmin).with(csrf()).param("linhas", "2")).andExpect(flash().attribute("falha", containsString("de 3 a")));
        mvc.perform(post("/admin/unidades/" + unidade.getId() + "/forno").with(sessaoAdmin).with(csrf()).param("linhas", "50")).andExpect(flash().attribute("sucesso", containsString("50 linhas")));
        assertThat(unidades.findById(unidade.getId()).orElseThrow().getLinhasForno()).isEqualTo(50);

        var descricao = "Motivo " + UUID.randomUUID();
        mvc.perform(post("/admin/motivos").with(sessaoAdmin).with(csrf()).param("tipo", "CORRECAO").param("descricao", descricao).param("exigeTexto", "true")).andExpect(flash().attributeExists("sucesso"));
        mvc.perform(post("/admin/motivos").with(sessaoAdmin).with(csrf()).param("tipo", "CORRECAO").param("descricao", descricao)).andExpect(flash().attribute("falha", containsString("já está na lista")));
        mvc.perform(post("/admin/motivos").with(autenticado(gestor)).with(csrf()).param("tipo", "CORRECAO").param("descricao", "x")).andExpect(status().isForbidden());
    }
    @Test void indicadoresFiltramPorEtapaEFormatoEMostramPatioEOcupacao() throws Exception {
        var sessaoRegistro = autenticado(registro); var sessaoGestor = autenticado(gestor);
        fornadas.abrirNova(unidade.getId(), gestor);
        var e = registrarEnforna(sessaoRegistro, 22);
        mvc.perform(post("/producao/validar-lote").with(sessaoGestor).with(csrf()).param("ids", "enforna:" + e.getId())).andExpect(status().is3xxRedirection());
        var base = get("/painel").param("unidade", unidade.getId().toString());
        // 22 of the 44 lines the kiln holds; the yard shows Maromba minus Enforna, negative here.
        mvc.perform(base.with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Movimento do pátio"))).andExpect(content().string(containsString("-35,42")))
            .andExpect(content().string(containsString("50%"))).andExpect(content().string(containsString("Acumulado")));
        mvc.perform(get("/painel").param("unidade", unidade.getId().toString()).param("etapa", "MAROMBA").with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Movimento do pátio")))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Últimas fornadas"))))
            .andExpect(content().string(containsString("Maromba por dia")));
        mvc.perform(get("/painel").param("unidade", unidade.getId().toString()).param("etapa", "ENFORNA").param("formato", SEIS_FUROS.toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Maromba por dia")))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("35,42"))));
        mvc.perform(get("/painel").param("etapa", "OUTRA").with(sessaoGestor)).andExpect(status().isBadRequest());
        mvc.perform(get("/painel/relatorio.pdf").param("unidade", unidade.getId().toString()).param("etapa", "ENFORNA").with(sessaoGestor)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
    }
    @Test void importacaoConfereDepoisEntraValidadaUmaVezSo() throws Exception {
        var sessaoAdmin = autenticado(admin);
        var url = "/admin/importacao";
        var hoje = producao.hoje();
        var ontem = hoje.minusDays(1);
        var cabecalho = ImportacaoService.CABECALHO + "\n";
        var dia = hoje.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        var mes = hoje.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy"));
        var bom = cabecalho + "MAROMBA;" + dia + ";;;8 furos;;;16.000;;;\nENFORNA;" + dia + ";" + mes + ";7;8F;10;;;;;\nDESENFORNA;" + dia + ";" + mes + ";7;8 furos;4;;;6000;400;40\n";
        // Without an inventory nothing enters.
        mvc.perform(post(url + "/conferir").with(sessaoAdmin).with(csrf()).param("unidade", unidade.getId().toString()).param("texto", bom))
            .andExpect(status().isOk()).andExpect(content().string(containsString("inventário inicial da unidade antes")));
        mvc.perform(post("/admin/inventario").with(sessaoAdmin).with(csrf()).param("idUnidade", unidade.getId().toString()).param("referencia", ontem.minusDays(1).atTime(18, 0).toString())
                .param("primeira[" + OITO_FUROS + "]", "1")).andExpect(status().is3xxRedirection());
        // A wrong line blocks everything and says which one.
        var errado = cabecalho + "MAROMBA;" + dia + ";;;8 furos;;;100;;;\nENFORNA;" + dia + ";" + mes + ";7;3 furos;10;;;;;\nMAROMBA;" + dia + ";;;8 furos;;;100;;;\nMAROMBA;31/02/2026;;;8 furos;;;100;;;\n";
        mvc.perform(post(url).with(sessaoAdmin).with(csrf()).param("unidade", unidade.getId().toString()).param("texto", errado)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Linha 3: formato"))).andExpect(content().string(containsString("Linha 4: esta etapa")))
            .andExpect(content().string(containsString("Linha 5: a data")));
        assertThat(fornadas.emAndamento(unidade.getId())).isEmpty();
        // Checking saves nothing; it only shows what would happen.
        mvc.perform(post(url + "/conferir").with(sessaoAdmin).with(csrf()).param("unidade", unidade.getId().toString()).param("texto", bom)).andExpect(status().isOk())
            .andExpect(content().string(containsString("3 lançamento(s) prontos para importar"))).andExpect(content().string(containsString("Importar 3 lançamento(s)")))
            .andExpect(content().string(containsString("7ª fornada de")));
        assertThat(fornadas.emAndamento(unidade.getId())).isEmpty();
        mvc.perform(post(url).with(autenticado(gestor)).with(csrf()).param("unidade", unidade.getId().toString()).param("texto", bom)).andExpect(status().isForbidden());

        mvc.perform(post(url).with(sessaoAdmin).with(csrf()).param("unidade", unidade.getId().toString()).param("texto", bom)).andExpect(flash().attribute("sucesso", startsWithTres()));
        var f = fornadas.emAndamento(unidade.getId()).getFirst();
        var en = enfornas.daFornada(f.getId()).getFirst();
        assertThat(en.getSituacao()).isEqualTo(Situacao.VALIDADO); assertThat(en.getOrigem()).isEqualTo(Origem.IMPORTADO);
        assertThat(en.getQuantidade()).isEqualTo(16100); assertThat(en.getValidador().getId()).isEqualTo(admin.id());
        // It enters the official numbers, and only once.
        mvc.perform(get("/painel").param("unidade", unidade.getId().toString()).with(autenticado(gestor))).andExpect(status().isOk()).andExpect(content().string(containsString("16,0")));
        mvc.perform(get(url).param("unidade", unidade.getId().toString()).with(sessaoAdmin)).andExpect(status().isOk()).andExpect(content().string(containsString("Importação feita")));
        mvc.perform(post(url).with(sessaoAdmin).with(csrf()).param("unidade", unidade.getId().toString()).param("texto", bom)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Importação feita")));
        assertThat(enfornas.daFornada(f.getId())).hasSize(1);
    }
    private static org.hamcrest.Matcher<String> startsWithTres() { return org.hamcrest.Matchers.startsWith("3 lançamento(s) importado(s)"); }
    @Test void lancamentosExportadosEmCsvComOsMesmosFiltrosEAcesso() throws Exception {
        var sessaoRegistro = autenticado(registro); var sessaoGestor = autenticado(gestor);
        fornadas.abrirNova(unidade.getId(), gestor);
        registrarEnforna(sessaoRegistro, 4);
        var hoje = producao.hoje().toString();
        mvc.perform(get("/producao").param("inicio", hoje).param("fim", hoje).with(sessaoGestor)).andExpect(status().isOk()).andExpect(content().string(containsString("Exportar CSV")));
        var csv = mvc.perform(get("/producao/exportar.csv").param("inicio", hoje).param("fim", hoje).param("etapa", "ENFORNA").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"lancamentos-" + hoje + "-a-" + hoje + ".csv\""))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Data;Etapa;").contains("Enforna").contains(";4;1610;6440;").contains("Pendente");
        // Registro de Produção exports only its own records.
        var outro = usuario(Perfil.REGISTRO_PRODUCAO, unidade);
        var deOutro = mvc.perform(get("/producao/exportar.csv").param("inicio", hoje).param("fim", hoje).with(autenticado(outro))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(deOutro).doesNotContain("6440");
        var soMaromba = mvc.perform(get("/producao/exportar.csv").param("inicio", hoje).param("fim", hoje).param("etapa", "MAROMBA").with(sessaoRegistro)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(soMaromba).doesNotContain("Enforna");
    }

    @Autowired MotivoRepository motivosTeste;
    @Test void gestorLancaJaValidadoCorrigePelaFornadaEContaOEstoque() throws Exception {
        var gestorDaUnidade = usuario(Perfil.GESTOR_PRODUCAO, unidade);
        var sessaoGestor = autenticado(gestorDaUnidade);
        fornadas.abrirNova(unidade.getId(), gestorDaUnidade);
        // What the Gestor de Produção registers enters Validated, with the warning block on the launch screen.
        mvc.perform(get("/lancamentos").with(sessaoGestor)).andExpect(status().isOk()).andExpect(content().string(containsString("Toda Enforna lançada agora entra nesta fornada")));
        var e = registrarEnforna(sessaoGestor, 4);
        assertThat(e.getSituacao()).isEqualTo(Situacao.VALIDADO); assertThat(e.getValidador().getId()).isEqualTo(gestorDaUnidade.id());
        // The load lists its records, and the correction opens in a window that comes back to Fornadas.
        mvc.perform(get("/fornadas").param("unidade", unidade.getId().toString()).with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("corrigir-" + e.getId()))).andExpect(content().string(containsString("Lançamentos desta fornada (1)")));
        var motivo = motivosTeste.findByTipoAndAtivoTrueOrderByDescricao(Motivo.Tipo.CORRECAO).getFirst();
        var voltar = "/fornadas?unidade=" + unidade.getId();
        mvc.perform(post("/producao/enforna/" + e.getId() + "/editar").with(sessaoGestor).with(csrf()).param("voltar", voltar)
                .param("chaveEnvio", e.getChaveEnvio().toString()).param("versao", String.valueOf(enfornas.findById(e.getId()).orElseThrow().getVersao()))
                .param("idFornada", e.getFornada().getId().toString()).param("data", e.getData().toString()).param("idMotivo", motivo.getId().toString())
                .param("textoMotivo", "Teste").param("itens[" + OITO_FUROS + "].linhas", "5").param("itens[" + OITO_FUROS + "].tijolosPorLinha", "1610")
                .param("itens[" + OITO_FUROS + "].quantidade", "8050"))
            .andExpect(redirectedUrl(voltar));
        assertThat(enfornas.findById(e.getId()).orElseThrow().getQuantidade()).isEqualTo(8050);

        // Yard count by the Gestor de Produção; the Gestor Geral reads it; the Registro does not see the stock.
        mvc.perform(post("/estoque").with(sessaoGestor).with(csrf()).param("unidade", unidade.getId().toString()).param("data", producao.hoje().toString())
                .param("primeira-" + OITO_FUROS, "22.915").param("segunda-" + OITO_FUROS, "3.440")).andExpect(flash().attribute("sucesso", "Contagem do pátio salva."));
        mvc.perform(get("/estoque").with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("22.915"))).andExpect(content().string(containsString("3.440"))).andExpect(content().string(containsString("Salvar contagem")));
        mvc.perform(get("/estoque").with(autenticado(usuario(Perfil.GESTOR_GERAL, null)))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Salvar contagem")))).andExpect(content().string(containsString("Fornadas em andamento")));
        mvc.perform(get("/estoque").with(autenticado(registro))).andExpect(status().isForbidden());
        mvc.perform(post("/estoque").with(autenticado(usuario(Perfil.GESTOR_GERAL, null))).with(csrf()).param("unidade", unidade.getId().toString())).andExpect(status().isForbidden());

        // The Administrador follows everything on Atividade recente.
        mvc.perform(get("/admin/atividade").with(autenticado(admin))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Estoque do pátio"))).andExpect(content().string(containsString("Registro corrigido")));
        mvc.perform(get("/admin/atividade").with(sessaoGestor)).andExpect(status().isForbidden());
    }
}
