package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Furnace loads: opening (RN06, RN08), firing times (RN21) and the manager's conclusion with the difference (RN08).
@Service
public class FornadaService {
    private final FornadaRepository fornadas; private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas;
    private final InventarioRepository inventarios; private final RegistroApoio apoio;
    public FornadaService(FornadaRepository fornadas, EnfornaRepository enfornas, DesenfornaRepository desenfornas,
            InventarioRepository inventarios, RegistroApoio apoio) {
        this.fornadas = fornadas; this.enfornas = enfornas; this.desenfornas = desenfornas; this.inventarios = inventarios; this.apoio = apoio;
    }
    public List<Fornada> emAndamento(UUID unidade) { return fornadas.findByUnidadeIdAndSituacaoOrderByMesAscNumeroAsc(unidade, Fornada.Situacao.EM_ANDAMENTO); }
    /** Loads that still take Enforna: in progress and not marked "Enforna concluída". */
    public List<Fornada> paraEnforna(UUID unidade) { return emAndamento(unidade).stream().filter(Fornada::isRecebendoEnforna).toList(); }
    /** RN08: a new load only when none is taking Enforna and fewer than two are in progress. */
    public boolean podeAbrirNova(UUID unidade) { return emAndamento(unidade).size() < 2 && paraEnforna(unidade).isEmpty(); }

    // Called inside the Enforna transaction when the user chooses "Nova fornada".
    Fornada abrirNova(Unidade unidade, Usuario autor) {
        apoio.travar("fornada-" + unidade.getId());
        if (!paraEnforna(unidade.getId()).isEmpty())
            throw new RegraNegocioException("Ainda há uma fornada recebendo Enforna. Marque a Enforna dela como concluída antes de começar outra.");
        if (emAndamento(unidade.getId()).size() >= 2)
            throw new RegraNegocioException("Já há duas fornadas em andamento. O Gestor de Produção precisa concluir a mais antiga antes.");
        var mes = YearMonth.from(apoio.hoje());
        var f = fornadas.saveAndFlush(new Fornada(unidade, mes, fornadas.ultimoNumero(unidade.getId(), mes.atDay(1)) + 1));
        apoio.auditar("fornada", f.getId(), autor, "CRIACAO", null, retrato(f), null, null);
        return f;
    }
    @Transactional(readOnly = true)
    public Fornada consultar(UUID id, UsuarioAutenticado p) {
        var u = apoio.usuarioAtivo(p);
        var f = fornadas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (u.getPerfil() == Perfil.REGISTRO_PRODUCAO && !f.getUnidade().getId().equals(u.getUnidade().getId())) apoio.negar();
        return f;
    }
    @Transactional(readOnly = true)
    public List<Fornada> recentes(UUID unidade, int quantidade) {
        return fornadas.recentes(unidade, org.springframework.data.domain.PageRequest.of(0, quantidade));
    }
    /** RN08 "Enforna concluída" and RN21 firing times, by the Registro de Produção of the unit or the Gestor. */
    @Transactional
    public Fornada registrarMarco(UUID id, UsuarioAutenticado p, long versao, Fornada.Marco marco, Instant quando) {
        var u = apoio.comPerfil(p, Perfil.REGISTRO_PRODUCAO, Perfil.GESTOR_PRODUCAO);
        var f = consultar(id, p);
        exigirEmAndamento(f, versao);
        if (quando == null || quando.isAfter(apoio.agora().plus(Duration.ofMinutes(5))))
            throw new RegraNegocioException("Confira o horário: ele não pode estar no futuro.");
        String antes = retrato(f);
        f.registrar(marco, quando);
        fornadas.flush(); apoio.auditar("fornada", f.getId(), u, "MARCO", antes, retrato(f), null, null);
        return f;
    }
    /** RN08: manual conclusion by the Gestor, showing the difference without asking for a justification. */
    @Transactional
    public Balanco concluir(UUID id, UsuarioAutenticado p, long versao) {
        var gestor = apoio.comPerfil(p, Perfil.GESTOR_PRODUCAO);
        var f = consultar(id, p);
        exigirEmAndamento(f, versao);
        var balanco = balanco(f);
        String antes = retrato(f);
        f.concluir(apoio.agora());
        fornadas.flush(); apoio.auditar("fornada", f.getId(), gestor, "CONCLUSAO", antes, retrato(f) + " · " + balanco.resumo(), null, null);
        return balanco;
    }
    private void exigirEmAndamento(Fornada f, long versao) {
        if (!f.isEmAndamento()) throw new RegraNegocioException("Esta fornada já foi concluída.");
        if (f.getVersao() != versao) throw new RegraNegocioException("Esta fornada mudou desde que você abriu a tela. Recarregue e confira novamente.");
    }

    @Transactional(readOnly = true)
    public Balanco balanco(UUID id, UsuarioAutenticado p) { return balanco(consultar(id, p)); }
    /** What went in (initial inventory + Enforna) and came out (Desenforna) of the load, per format; cancelled records do not count. */
    Balanco balanco(Fornada f) {
        var porFormato = new LinkedHashMap<UUID, Balanco.Acumulado>();
        for (var i : inventarios.findByFornadaId(f.getId()))
            porFormato.computeIfAbsent(i.getFormato().getId(), k -> new Balanco.Acumulado(i.getFormato()))
                .entrou(i.getLinhas() == null ? 0 : i.getLinhas(), i.getQuantidade());
        for (var e : enfornas.daFornada(f.getId()))
            porFormato.computeIfAbsent(e.getFormato().getId(), k -> new Balanco.Acumulado(e.getFormato())).entrou(e.getLinhas(), e.getQuantidade());
        for (var d : desenfornas.daFornada(f.getId()))
            porFormato.computeIfAbsent(d.getFormato().getId(), k -> new Balanco.Acumulado(d.getFormato())).saiu(d);
        var formatos = porFormato.values().stream().map(Balanco.Acumulado::fechar)
            .sorted(Comparator.comparing(x -> x.formato().getNome())).toList();
        return new Balanco(f, formatos, horas(f.getInicioEsquente(), f.getInicioQueima()), horas(f.getInicioQueima(), f.getFimQueima()));
    }
    private static Double horas(Instant inicio, Instant fim) {
        return inicio == null || fim == null ? null : Duration.between(inicio, fim).toMinutes() / 60.0;
    }
    static String retrato(Fornada f) {
        var t = new StringBuilder(f.getIdentificacao() + " · " + (f.isEmAndamento() ? "Em andamento" : "Concluída"));
        if (f.getEnfornaConcluidaEm() != null) t.append(" · Enforna concluída ").append(f.getEnfornaConcluidaEm());
        if (f.getInicioEsquente() != null) t.append(" · Esquente ").append(f.getInicioEsquente());
        if (f.getInicioQueima() != null) t.append(" · Queima ").append(f.getInicioQueima());
        if (f.getFimQueima() != null) t.append(" · Fim da queima ").append(f.getFimQueima());
        return t.toString();
    }
}
