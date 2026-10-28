package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.auditoria.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProducaoService {
    public static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    private final LancamentoRepository lancamentos; private final UsuarioRepository usuarios;
    private final AuditoriaRepository auditoria; private final JdbcTemplate jdbc; private final Clock clock;
    public ProducaoService(LancamentoRepository lancamentos, UsuarioRepository usuarios, AuditoriaRepository auditoria, JdbcTemplate jdbc, Clock clock) {
        this.lancamentos = lancamentos; this.usuarios = usuarios; this.auditoria = auditoria; this.jdbc = jdbc; this.clock = clock;
    }
    public LocalDate hoje() { return LocalDate.now(clock.withZone(FUSO)); }
    @Transactional
    public Lancamento criar(UsuarioAutenticado p, MarombaForm f) {
        var autor = usuarioAtivo(p);
        if (autor.getPerfil() != Perfil.SUPERVISOR) negar();
        validarValores(f);
        // PostgreSQL transaction lock serializes retries of the exact same request key.
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, f.getChaveEnvio().toString());
        String impressao = impressao(autor, f);
        var anterior = lancamentos.findByChaveEnvio(f.getChaveEnvio());
        if (anterior.isPresent()) {
            var salvo = anterior.get();
            if (!salvo.getAutor().getId().equals(autor.getId()) || !salvo.getImpressaoEnvio().equals(impressao))
                throw new RegraNegocioException("Este envio já foi usado com outros dados. Consulte seus registros e abra um novo formulário.");
            return salvo;
        }
        if (lancamentos.existsByUnidadeIdAndDataProducao(autor.getUnidade().getId(), f.getData()))
            throw new RegraNegocioException("Já existe uma Maromba para esta data e unidade. Consulte o registro para conferir ou corrigir.");
        var l = lancamentos.saveAndFlush(new Lancamento(autor.getUnidade(), autor, f.getData(), f.getQuantidade6f(), f.getQuantidade8f(), f.getChaveEnvio(), impressao, clock.instant()));
        auditar(l, autor, "CRIACAO", null);
        return l;
    }
    @Transactional(readOnly = true)
    public Lancamento consultar(UUID id, UsuarioAutenticado p) {
        var usuario = usuarioAtivo(p);
        var l = lancamentos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (usuario.getPerfil() == Perfil.ADMINISTRADOR) negar();
        if (usuario.getPerfil() == Perfil.SUPERVISOR && (!l.getAutor().getId().equals(usuario.getId())
                || !l.getUnidade().getId().equals(usuario.getUnidade().getId()))) negar();
        return l;
    }
    @Transactional
    public Lancamento editar(UUID id, UsuarioAutenticado p, MarombaForm f) {
        var l = consultar(id, p);
        exigirEdicao(l, p);
        conferirVersao(l, f.getVersao()); validarValores(f);
        String antes = l.retrato();
        l.editar(f.getData(), f.getQuantidade6f(), f.getQuantidade8f());
        if (!antes.equals(l.retrato())) {
            lancamentos.flush(); auditar(l, usuarioAtivo(p), "EDICAO", antes);
        }
        return l;
    }
    @Transactional
    public Lancamento validar(UUID id, UsuarioAutenticado p, long versao) {
        var gestor = usuarioAtivo(p);
        if (gestor.getPerfil() != Perfil.GESTOR_PRODUCAO) negar();
        var l = consultar(id, p); conferirVersao(l, versao);
        if (l.getSituacao() != Situacao.PENDENTE) throw new RegraNegocioException("Este lançamento já foi validado.");
        String antes = l.retrato(); l.validar(gestor, clock.instant());
        lancamentos.flush(); auditar(l, gestor, "VALIDACAO", antes); return l;
    }
    public void exigirEdicao(Lancamento l, UsuarioAutenticado p) {
        if ((p.perfil() != Perfil.SUPERVISOR && p.perfil() != Perfil.GESTOR_PRODUCAO) || l.getSituacao() != Situacao.PENDENTE) negar();
    }
    private Usuario usuarioAtivo(UsuarioAutenticado p) {
        if (p == null) throw new AccessDeniedException("Entre novamente.");
        var u = usuarios.findById(p.id()).orElseThrow(() -> new AccessDeniedException("Acesso indisponível."));
        if (!u.podeEntrar() || u.getPerfil() != p.perfil() || u.getVersaoAcesso() != p.versaoAcesso()) negar();
        return u;
    }
    private void validarValores(MarombaForm f) {
        if (f.getChaveEnvio() == null || f.getData() == null || f.getData().isAfter(hoje()))
            throw new RegraNegocioException("Confira a data: não é possível registrar uma produção futura.");
        if (f.getQuantidade6f() == null || f.getQuantidade8f() == null || f.getQuantidade6f() < 0 || f.getQuantidade8f() < 0
                || f.getQuantidade6f() > 1000000000 || f.getQuantidade8f() > 1000000000)
            throw new RegraNegocioException("Preencha as duas quantidades com números inteiros maiores ou iguais a zero.");
        if (f.getQuantidade6f() == 0 && f.getQuantidade8f() == 0)
            throw new RegraNegocioException("Informe ao menos uma quantidade maior que zero.");
    }
    private void conferirVersao(Lancamento l, long versao) {
        if (l.getVersao() != versao) throw new RegraNegocioException("Este registro mudou desde que você abriu a tela. Recarregue e confira novamente.");
    }
    private void auditar(Lancamento l, Usuario u, String acao, String antes) {
        auditoria.save(new EventoAuditoria(l.getId(), u, acao, antes, l.retrato(), clock.instant()));
    }
    private String impressao(Usuario u, MarombaForm f) {
        String texto = u.getId() + "|" + u.getUnidade().getId() + "|" + f.getData() + "|" + f.getQuantidade6f() + "|" + f.getQuantidade8f();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void negar() { throw new AccessDeniedException("Seu perfil não permite esta operação."); }
}
