package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Furnace loads: the Gestor de Produção opens and closes them (RN06, RN08) and may mark the firing times (RN21).
@Service
public class FornadaService {
    private final FornadaRepository fornadas; private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas;
    private final InventarioRepository inventarios; private final UnidadeRepository unidades; private final RegistroApoio apoio;
    public FornadaService(FornadaRepository fornadas, EnfornaRepository enfornas, DesenfornaRepository desenfornas,
            InventarioRepository inventarios, UnidadeRepository unidades, RegistroApoio apoio) {
        this.fornadas = fornadas; this.enfornas = enfornas; this.desenfornas = desenfornas; this.inventarios = inventarios;
        this.unidades = unidades; this.apoio = apoio;
    }
    public List<Fornada> emAndamento(UUID unidade) { return fornadas.findByUnidadeIdAndSituacaoOrderByMesAscNumeroAsc(unidade, Fornada.Situacao.EM_ANDAMENTO); }
    /** The load taking Enforna: the last one the Gestor opened, while it is in progress. */
    public Optional<Fornada> paraEnforna(UUID unidade) { return emAndamento(unidade).stream().filter(Fornada::isRecebendoEnforna).reduce((a, b) -> b); }
    /** RN08: at most two loads in progress, one being emptied and one being filled. */
    public boolean podeAbrirNova(UUID unidade) { return emAndamento(unidade).size() < 2; }

    /** RN08: the Gestor de Produção opens the next load; from then on the previous one takes no more Enforna. */
    @Transactional
    public Fornada abrirNova(UUID idUnidade, UsuarioAutenticado p) {
        var gestor = apoio.comPerfil(p, Perfil.GESTOR_PRODUCAO);
        var unidade = unidades.findById(idUnidade).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        apoio.travar("fornada-" + idUnidade);
        var abertas = emAndamento(idUnidade);
        if (abertas.size() >= 2)
            throw new RegraNegocioException("Já há duas fornadas em andamento. Feche a mais antiga antes de abrir outra.");
        var agora = apoio.agora();
        for (var a : abertas)
            if (a.isRecebendoEnforna()) {
                String antes = retrato(a);
                a.registrar(Fornada.Marco.ENFORNA_CONCLUIDA, agora);
                apoio.auditar("fornada", a.getId(), gestor, "MARCO", antes, retrato(a), null, null);
            }
        var mes = YearMonth.from(apoio.hoje());
        var f = fornadas.saveAndFlush(new Fornada(unidade, mes, fornadas.ultimoNumero(idUnidade, mes.atDay(1)) + 1));
        apoio.auditar("fornada", f.getId(), gestor, "CRIACAO", null, retrato(f), null, null);
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
    /** RN21: optional firing times, marked by the Gestor de Produção when the factory reports them. */
    @Transactional
    public Fornada registrarMarco(UUID id, UsuarioAutenticado p, long versao, Fornada.Marco marco, Instant quando) {
        var u = apoio.comPerfil(p, Perfil.GESTOR_PRODUCAO);
        if (!marco.isManual()) throw new RegraNegocioException("Este horário é preenchido pelo sistema.");
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
        // Only the Registro's records can be Pending: the Gestor's own enter Validated.
        long pendentes = enfornas.daFornada(f.getId()).stream().filter(r -> r.getSituacao() == Situacao.PENDENTE).count()
            + desenfornas.daFornada(f.getId()).stream().filter(r -> r.getSituacao() == Situacao.PENDENTE).count();
        if (pendentes > 0) throw new RegraNegocioException("A " + f.getIdentificacao() + " tem " + pendentes + " lançamento(s) do Registro pendente(s). Valide ou corrija antes de fechar.");
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
        var queima = inicioQueima(f);
        return new Balanco(f, formatos, horas(f.getInicioEsquente(), queima), horas(queima, f.getFimQueima()), f.getInicioQueima() == null);
    }
    /** The esquente takes about 24 hours (field visit); older loads may have the burning start recorded by hand. */
    static final Duration ESQUENTE = Duration.ofHours(24);
    private static Instant inicioQueima(Fornada f) {
        if (f.getInicioQueima() != null) return f.getInicioQueima();
        return f.getInicioEsquente() == null ? null : f.getInicioEsquente().plus(ESQUENTE);
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
