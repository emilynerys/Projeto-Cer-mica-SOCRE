package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.auditoria.*;
import br.com.socre.producao.cadastro.*;
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
    private static final String TABELA = "maromba";
    private final MarombaRepository marombas; private final FormatoRepository formatos; private final UsuarioRepository usuarios;
    private final AuditoriaRepository auditoria; private final JdbcTemplate jdbc; private final Clock clock;
    public ProducaoService(MarombaRepository marombas, FormatoRepository formatos, UsuarioRepository usuarios, AuditoriaRepository auditoria, JdbcTemplate jdbc, Clock clock) {
        this.marombas = marombas; this.formatos = formatos; this.usuarios = usuarios; this.auditoria = auditoria; this.jdbc = jdbc; this.clock = clock;
    }
    public LocalDate hoje() { return LocalDate.now(clock.withZone(FUSO)); }
    public List<Formato> formatosAtivos() { return formatos.findByAtivoTrueOrderByNome(); }
    // Each active format with a quantity above zero becomes one record; all of them share the submission key.
    @Transactional
    public List<Maromba> criar(UsuarioAutenticado p, MarombaForm f) {
        var autor = usuarioAtivo(p);
        if (autor.getPerfil() != Perfil.REGISTRO_PRODUCAO) negar();
        var ativos = formatosAtivos();
        validarCriacao(f, ativos);
        // PostgreSQL transaction lock serializes retries of the exact same request key.
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, f.getChaveEnvio().toString());
        String impressao = impressao(autor, f);
        var anteriores = marombas.findByChaveEnvioOrderByFormatoNome(f.getChaveEnvio());
        if (!anteriores.isEmpty()) {
            if (anteriores.stream().anyMatch(m -> !m.getAutor().getId().equals(autor.getId()) || !m.getImpressaoEnvio().equals(impressao)))
                throw new RegraNegocioException("Este envio já foi usado com outros dados. Consulte seus registros e abra um novo formulário.");
            return anteriores;
        }
        var criados = new ArrayList<Maromba>();
        for (var formato : ativos) {
            int quantidade = f.getQuantidades().get(formato.getId());
            if (quantidade == 0) continue;
            if (marombas.existeAtivo(autor.getUnidade().getId(), formato.getId(), f.getData()))
                throw new RegraNegocioException("Já existe Maromba de " + formato.getNome() + " para esta data e unidade. Consulte o registro para conferir ou corrigir.");
            criados.add(marombas.saveAndFlush(new Maromba(autor.getUnidade(), formato, autor, f.getData(), quantidade, f.getChaveEnvio(), impressao, clock.instant())));
        }
        for (var m : criados) auditar(m, autor, "CRIACAO", null);
        return criados;
    }
    @Transactional(readOnly = true)
    public Maromba consultar(UUID id, UsuarioAutenticado p) {
        var usuario = usuarioAtivo(p);
        var m = marombas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (usuario.getPerfil() == Perfil.ADMINISTRADOR) negar();
        if (usuario.getPerfil() == Perfil.REGISTRO_PRODUCAO && (!m.getAutor().getId().equals(usuario.getId())
                || !m.getUnidade().getId().equals(usuario.getUnidade().getId()))) negar();
        return m;
    }
    // A correction changes one record, that is, one format.
    @Transactional
    public Maromba editar(UUID id, UsuarioAutenticado p, MarombaForm f) {
        var m = consultar(id, p);
        exigirEdicao(m, p);
        conferirVersao(m, f.getVersao());
        validarData(f);
        Integer quantidade = f.getQuantidades().get(m.getFormato().getId());
        if (quantidade == null || quantidade <= 0 || quantidade > 1000000000)
            throw new RegraNegocioException("Informe uma quantidade inteira maior que zero para " + m.getFormato().getNome() + ".");
        String antes = m.retrato();
        m.editar(f.getData(), quantidade);
        if (!antes.equals(m.retrato())) {
            marombas.flush(); auditar(m, usuarioAtivo(p), "CORRECAO", antes);
        }
        return m;
    }
    @Transactional
    public Maromba validar(UUID id, UsuarioAutenticado p, long versao) {
        var gestor = usuarioAtivo(p);
        if (gestor.getPerfil() != Perfil.GESTOR_PRODUCAO) negar();
        var m = consultar(id, p); conferirVersao(m, versao);
        if (m.getSituacao() != Situacao.PENDENTE) throw new RegraNegocioException("Este registro já foi validado.");
        String antes = m.retrato(); m.validar(gestor, clock.instant());
        marombas.flush(); auditar(m, gestor, "VALIDACAO", antes); return m;
    }
    public List<EventoAuditoria> historico(UUID id) { return auditoria.findByTabelaAndIdRegistroOrderByDataHoraAsc(TABELA, id); }
    public void exigirEdicao(Maromba m, UsuarioAutenticado p) {
        if ((p.perfil() != Perfil.REGISTRO_PRODUCAO && p.perfil() != Perfil.GESTOR_PRODUCAO) || m.getSituacao() != Situacao.PENDENTE) negar();
    }
    private Usuario usuarioAtivo(UsuarioAutenticado p) {
        if (p == null) throw new AccessDeniedException("Entre novamente.");
        var u = usuarios.findById(p.id()).orElseThrow(() -> new AccessDeniedException("Acesso indisponível."));
        if (!u.podeEntrar() || u.getPerfil() != p.perfil() || u.getVersaoAcesso() != p.versaoAcesso()) negar();
        return u;
    }
    private void validarData(MarombaForm f) {
        if (f.getChaveEnvio() == null || f.getData() == null || f.getData().isAfter(hoje()))
            throw new RegraNegocioException("Confira a data: não é possível registrar uma produção futura.");
    }
    private void validarCriacao(MarombaForm f, List<Formato> ativos) {
        validarData(f);
        var ids = ativos.stream().map(Formato::getId).toList();
        if (!f.getQuantidades().keySet().equals(new HashSet<>(ids)) || f.getQuantidades().values().stream()
                .anyMatch(q -> q == null || q < 0 || q > 1000000000))
            throw new RegraNegocioException("Preencha todos os formatos com números inteiros maiores ou iguais a zero.");
        if (f.getQuantidades().values().stream().allMatch(q -> q == 0))
            throw new RegraNegocioException("Informe ao menos uma quantidade maior que zero.");
    }
    private void conferirVersao(Maromba m, long versao) {
        if (m.getVersao() != versao) throw new RegraNegocioException("Este registro mudou desde que você abriu a tela. Recarregue e confira novamente.");
    }
    private void auditar(Maromba m, Usuario u, String acao, String antes) {
        auditoria.save(new EventoAuditoria(TABELA, m.getId(), u, acao, antes, m.retrato(), clock.instant()));
    }
    private String impressao(Usuario u, MarombaForm f) {
        var texto = new StringBuilder(u.getId() + "|" + u.getUnidade().getId() + "|" + f.getData());
        new TreeMap<>(f.getQuantidades()).forEach((formato, q) -> texto.append('|').append(formato).append('=').append(q));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void negar() { throw new AccessDeniedException("Seu perfil não permite esta operação."); }
}
