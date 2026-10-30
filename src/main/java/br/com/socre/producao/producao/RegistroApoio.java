package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.auditoria.*;
import br.com.socre.producao.cadastro.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

// Rules shared by the three stages: who may see or change a record, reasons, audit trail and dates.
@Component
public class RegistroApoio {
    public static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    private final UsuarioRepository usuarios; private final AuditoriaRepository auditoria; private final MotivoRepository motivos;
    private final InventarioRepository inventarios; private final JdbcTemplate jdbc; private final Clock clock;
    public RegistroApoio(UsuarioRepository usuarios, AuditoriaRepository auditoria, MotivoRepository motivos,
            InventarioRepository inventarios, JdbcTemplate jdbc, Clock clock) {
        this.usuarios = usuarios; this.auditoria = auditoria; this.motivos = motivos;
        this.inventarios = inventarios; this.jdbc = jdbc; this.clock = clock;
    }
    public Instant agora() { return clock.instant(); }
    public LocalDate hoje() { return LocalDate.now(clock.withZone(FUSO)); }

    public Usuario usuarioAtivo(UsuarioAutenticado p) {
        if (p == null) throw new AccessDeniedException("Entre novamente.");
        var u = usuarios.findById(p.id()).orElseThrow(() -> new AccessDeniedException("Acesso indisponível."));
        if (!u.podeEntrar() || u.getPerfil() != p.perfil() || u.getVersaoAcesso() != p.versaoAcesso()) negar();
        return u;
    }
    public Usuario comPerfil(UsuarioAutenticado p, Perfil... perfis) {
        var u = usuarioAtivo(p);
        if (!Arrays.asList(perfis).contains(u.getPerfil())) negar();
        return u;
    }
    public void negar() { throw new AccessDeniedException("Seu perfil não permite esta operação."); }

    /** Registro de Produção sees only its own records of its unit; the other profiles read everything (RN05). */
    public void exigirLeitura(RegistroEtapa r, Usuario u) {
        if (u.getPerfil() == Perfil.REGISTRO_PRODUCAO && (!r.getAutor().getId().equals(u.getId())
                || !r.getUnidade().getId().equals(u.getUnidade().getId()))) negar();
    }
    /** RN11, RN15 and RN16: Registro de Produção changes only its own Pending records; Gestor de Produção any record not cancelled. */
    public void exigirAlteracao(RegistroEtapa r, Usuario u) {
        exigirLeitura(r, u);
        boolean pode = switch (u.getPerfil()) {
            case REGISTRO_PRODUCAO -> r.getSituacao() == Situacao.PENDENTE;
            case GESTOR_PRODUCAO -> r.getSituacao() != Situacao.CANCELADO;
            default -> false;
        };
        if (!pode) negar();
    }
    public boolean podeAlterar(RegistroEtapa r, UsuarioAutenticado p) {
        return switch (p.perfil()) {
            case REGISTRO_PRODUCAO -> r.getSituacao() == Situacao.PENDENTE;
            case GESTOR_PRODUCAO -> r.getSituacao() != Situacao.CANCELADO;
            default -> false;
        };
    }
    public void conferirVersao(RegistroEtapa r, long versao) {
        if (r.getVersao() != versao) throw new RegraNegocioException("Este registro mudou desde que você abriu a tela. Recarregue e confira novamente.");
    }

    /** RN17 (never in the future) and RN20 (only after the unit's initial inventory, which already holds that day). */
    public void conferirData(Unidade unidade, LocalDate data) {
        if (data == null || data.isAfter(hoje()))
            throw new RegraNegocioException("Confira a data: não é possível registrar uma produção futura.");
        inventarios.referencia(unidade.getId()).map(r -> LocalDate.ofInstant(r, FUSO)).ifPresent(ref -> {
            if (!data.isAfter(ref))
                throw new RegraNegocioException("Confira a data: a produção até " + ref.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " já está no inventário inicial da unidade.");
        });
    }

    /** RN15 and RN16: a reason from the configurable list; "Outro" also needs a description. */
    public Motivo motivo(UUID id, String texto, Motivo.Tipo tipo) {
        var nome = tipo == Motivo.Tipo.CORRECAO ? "da correção" : "do cancelamento";
        if (id == null) throw new RegraNegocioException("Escolha o motivo " + nome + ".");
        var m = motivos.findById(id).filter(x -> x.getTipo() == tipo && x.isAtivo())
            .orElseThrow(() -> new RegraNegocioException("Escolha um motivo " + nome + " da lista."));
        if (m.isExigeTexto() && (texto == null || texto.isBlank()))
            throw new RegraNegocioException("Descreva o motivo " + nome + ".");
        return m;
    }
    public List<Motivo> motivos(Motivo.Tipo tipo) { return motivos.findByTipoAndAtivoTrueOrderByDescricao(tipo); }

    /** PostgreSQL transaction lock that serializes retries of the same submission key. */
    public void travar(String chave) { jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, chave); }
    public String impressao(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public void auditar(RegistroEtapa r, Usuario u, String acao, String antes) { auditar(r, u, acao, antes, null, null); }
    public void auditar(RegistroEtapa r, Usuario u, String acao, String antes, Motivo motivo, String texto) {
        auditar(r.getTabela(), r.getId(), u, acao, antes, r.retrato(), motivo, texto);
    }
    public void auditar(String tabela, UUID id, Usuario u, String acao, String antes, String depois, Motivo motivo, String texto) {
        auditoria.save(new EventoAuditoria(tabela, id, u, acao, antes, depois, agora(), motivo,
            texto == null || texto.isBlank() ? null : texto.strip()));
    }
    public List<EventoAuditoria> historico(String tabela, UUID id) { return auditoria.findByTabelaAndIdRegistroOrderByDataHoraAsc(tabela, id); }
}
