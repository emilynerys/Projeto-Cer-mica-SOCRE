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
    private RequestPostProcessor autenticado(UsuarioAutenticado p) throws Exception {
        var resultado = mvc.perform(post("/login").with(csrf()).param("email", p.email()).param("password", SENHA))
            .andExpect(redirectedUrl("/")).andReturn();
        var cookie = resultado.getResponse().getCookie("SOCRE_SESSION");
        String id = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        var sessao = sessoes.findById(id);
        ((org.springframework.session.Session) sessao).setAttribute(PoliticaSessao.AUTENTICADO_EM, Instant.now()); sessoes.save(sessao);
        return request -> { request.setCookies(cookie); return request; };
    }
    /** Posts a new Enforna of 8 furos in a new load and returns the created record. */
    private Enforna registrarEnforna(RequestPostProcessor sessao, int linhas) throws Exception {
        var url = mvc.perform(post("/producao/enforna/nova").with(sessao).with(csrf())
                .param("data", producao.hoje().toString()).param("chaveEnvio", UUID.randomUUID().toString()).param("versao", "0").param("idFornada", "")
                .param("itens[" + SEIS_FUROS + "].linhas", "0").param("itens[" + SEIS_FUROS + "].tijolosPorLinha", "2200").param("itens[" + SEIS_FUROS + "].quantidade", "0")
                .param("itens[" + OITO_FUROS + "].linhas", String.valueOf(linhas)).param("itens[" + OITO_FUROS + "].tijolosPorLinha", "1610")
                .param("itens[" + OITO_FUROS + "].quantidade", String.valueOf(linhas * 1610)))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(url).startsWith("/producao/enforna/");
        return enfornas.findById(UUID.fromString(url.substring(url.lastIndexOf('/') + 1))).orElseThrow();
    }

    @Test void enfornaPelaTelaAbreFornadaEMostraODetalhe() throws Exception {
        var sessao = autenticado(registro);
        mvc.perform(get("/producao/enforna/nova").with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Começar nova fornada"))).andExpect(content().string(containsString("Tijolos por linha")));
        var e = registrarEnforna(sessao, 10);
        assertThat(e.getQuantidade()).isEqualTo(16100);
        assertThat(e.getFornada().getNumero()).isEqualTo(1);
        mvc.perform(get("/producao/enforna/" + e.getId()).with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString(e.getFornada().getIdentificacao()))).andExpect(content().string(containsString("16.100 tijolos")));
        // The open load is offered again instead of a new one.
        mvc.perform(get("/producao/enforna/nova").with(sessao)).andExpect(status().isOk())
            .andExpect(content().string(containsString(e.getFornada().getIdentificacao())))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Começar nova fornada"))));
    }

    @Test void desenfornaPelaTelaEListaUnicaComValidacaoEmLote() throws Exception {
        var sessaoRegistro = autenticado(registro);
        var e = registrarEnforna(sessaoRegistro, 4);
        mvc.perform(get("/producao/desenforna/nova").with(sessaoRegistro)).andExpect(status().isOk()).andExpect(content().string(containsString("1ª qualidade")));
        mvc.perform(post("/producao/desenforna/nova").with(sessaoRegistro).with(csrf())
                .param("data", producao.hoje().toString()).param("chaveEnvio", UUID.randomUUID().toString()).param("versao", "0").param("idFornada", e.getFornada().getId().toString())
                .param("itens[" + SEIS_FUROS + "].linhas", "0").param("itens[" + SEIS_FUROS + "].primeira", "0").param("itens[" + SEIS_FUROS + "].segunda", "0").param("itens[" + SEIS_FUROS + "].cru", "0")
                .param("itens[" + OITO_FUROS + "].linhas", "4").param("itens[" + OITO_FUROS + "].primeira", "6000").param("itens[" + OITO_FUROS + "].segunda", "400").param("itens[" + OITO_FUROS + "].cru", "40"))
            .andExpect(status().is3xxRedirection());
        var d = desenfornas.daFornada(e.getFornada().getId()).getFirst();
        mvc.perform(get("/producao").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Enforna"))).andExpect(content().string(containsString("Desenforna")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Validar selecionados"))));

        var sessaoGestor = autenticado(gestor);
        mvc.perform(get("/producao").param("etapa", "DESENFORNA").with(sessaoGestor)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Validar selecionados"))).andExpect(content().string(containsString("desenforna:" + d.getId())));
        mvc.perform(post("/producao/validar-lote").with(sessaoGestor).with(csrf()).param("ids", "enforna:" + e.getId(), "desenforna:" + d.getId()))
            .andExpect(redirectedUrl("/producao")).andExpect(flash().attribute("sucesso", "2 registro(s) validado(s)."));
        assertThat(enfornas.findById(e.getId()).orElseThrow().getSituacao()).isEqualTo(Situacao.VALIDADO);
        assertThat(desenfornas.findById(d.getId()).orElseThrow().getSituacao()).isEqualTo(Situacao.VALIDADO);
        mvc.perform(post("/producao/validar-lote").with(sessaoRegistro).with(csrf()).param("ids", "enforna:" + e.getId())).andExpect(status().isForbidden());
    }

    @Test void fornadasMostraBalancoEMarcosPorPerfil() throws Exception {
        var sessaoRegistro = autenticado(registro);
        var e = registrarEnforna(sessaoRegistro, 6);
        var f = e.getFornada();
        mvc.perform(get("/fornadas").with(sessaoRegistro)).andExpect(status().isOk())
            .andExpect(content().string(containsString(f.getIdentificacao()))).andExpect(content().string(containsString("Registrar horário")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Concluir esta fornada"))));
        mvc.perform(post("/fornadas/" + f.getId() + "/marco").with(sessaoRegistro).with(csrf()).param("versao", String.valueOf(f.getVersao())).param("marco", "ENFORNA_CONCLUIDA"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("sucesso"));
        assertThat(fornadas.consultar(f.getId(), registro).isRecebendoEnforna()).isFalse();

        var sessaoAdmin = autenticado(admin);
        mvc.perform(get("/fornadas").param("unidade", unidade.getId().toString()).with(sessaoAdmin)).andExpect(status().isOk())
            .andExpect(content().string(containsString(f.getIdentificacao()))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Registrar horário"))));
        mvc.perform(post("/fornadas/" + f.getId() + "/concluir").with(sessaoAdmin).with(csrf()).param("versao", "1")).andExpect(status().isForbidden());
        mvc.perform(get("/producao/enforna/nova").with(autenticado(gestor))).andExpect(status().isForbidden());
        mvc.perform(get("/fornadas").param("unidade", unidade.getId().toString()).with(autenticado(gestor))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Concluir esta fornada")));
    }
}
