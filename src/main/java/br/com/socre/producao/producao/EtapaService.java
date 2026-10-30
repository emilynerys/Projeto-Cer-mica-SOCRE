package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.auditoria.EventoAuditoria;
import br.com.socre.producao.cadastro.Motivo;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Operations that work the same way on Maromba, Enforna and Desenforna records.
public abstract class EtapaService<T extends RegistroEtapa> {
    protected final JpaRepository<T, UUID> registros; protected final RegistroApoio apoio;
    protected EtapaService(JpaRepository<T, UUID> registros, RegistroApoio apoio) { this.registros = registros; this.apoio = apoio; }
    protected abstract String tabela();

    @Transactional(readOnly = true)
    public T consultar(UUID id, UsuarioAutenticado p) {
        var u = apoio.usuarioAtivo(p);
        var r = registros.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        apoio.exigirLeitura(r, u);
        return r;
    }
    @Transactional
    public T validar(UUID id, UsuarioAutenticado p, long versao) {
        var gestor = apoio.comPerfil(p, Perfil.GESTOR_PRODUCAO);
        var r = consultar(id, p); apoio.conferirVersao(r, versao);
        validar(r, gestor);
        return r;
    }
    /** Validates every Pending record of the list; the others are left as they are (RF36). */
    @Transactional
    public int validarLote(Collection<UUID> ids, UsuarioAutenticado p) {
        var gestor = apoio.comPerfil(p, Perfil.GESTOR_PRODUCAO);
        int validados = 0;
        for (var r : registros.findAllById(ids)) {
            if (r.getSituacao() != Situacao.PENDENTE) continue;
            validar(r, gestor); validados++;
        }
        return validados;
    }
    private void validar(T r, Usuario gestor) {
        if (r.getSituacao() != Situacao.PENDENTE) throw new RegraNegocioException("Só registros pendentes podem ser validados.");
        String antes = r.retrato(); r.validar(gestor, apoio.agora());
        registros.flush(); apoio.auditar(r, gestor, "VALIDACAO", antes);
    }
    /** RN16: the record should not exist; it leaves the indicators but stays for consultation and frees its key (RN09). */
    @Transactional
    public T cancelar(UUID id, UsuarioAutenticado p, long versao, UUID idMotivo, String textoMotivo) {
        var u = apoio.comPerfil(p, Perfil.REGISTRO_PRODUCAO, Perfil.GESTOR_PRODUCAO);
        var r = consultar(id, p);
        apoio.exigirAlteracao(r, u); apoio.conferirVersao(r, versao);
        var motivo = apoio.motivo(idMotivo, textoMotivo, Motivo.Tipo.CANCELAMENTO);
        String antes = r.retrato(); r.cancelar();
        registros.flush(); apoio.auditar(r, u, "CANCELAMENTO", antes, motivo, textoMotivo);
        return r;
    }
    /** Correction preamble shared by the stages (RN11, RN15). */
    protected Correcao<T> iniciarCorrecao(UUID id, UsuarioAutenticado p, long versao, UUID idMotivo, String textoMotivo) {
        var u = apoio.comPerfil(p, Perfil.REGISTRO_PRODUCAO, Perfil.GESTOR_PRODUCAO);
        var r = consultar(id, p);
        apoio.exigirAlteracao(r, u); apoio.conferirVersao(r, versao);
        var motivo = apoio.motivo(idMotivo, textoMotivo, Motivo.Tipo.CORRECAO);
        return new Correcao<>(r, u, motivo, textoMotivo, r.retrato());
    }
    protected T concluirCorrecao(Correcao<T> c) {
        if (!c.antes().equals(c.registro().retrato())) {
            registros.flush(); apoio.auditar(c.registro(), c.usuario(), "CORRECAO", c.antes(), c.motivo(), c.texto());
        }
        return c.registro();
    }
    protected record Correcao<T>(T registro, Usuario usuario, Motivo motivo, String texto, String antes) {}

    public boolean podeAlterar(T r, UsuarioAutenticado p) { return apoio.podeAlterar(r, p); }
    public List<EventoAuditoria> historico(UUID id) { return apoio.historico(tabela(), id); }
    public List<Motivo> motivos(Motivo.Tipo tipo) { return apoio.motivos(tipo); }
}
