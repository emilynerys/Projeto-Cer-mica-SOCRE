package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Status do Forno: the Gestor de Produção or the Gestor Geral types the operator's paper; every profile reads it.
@Service
public class StatusFornoService {
    private final StatusFornoRepository status; private final UnidadeRepository unidades; private final FormatoRepository formatos;
    private final FornadaService fornadas; private final RegistroApoio apoio;
    public StatusFornoService(StatusFornoRepository status, UnidadeRepository unidades, FormatoRepository formatos, FornadaService fornadas, RegistroApoio apoio) {
        this.status = status; this.unidades = unidades; this.formatos = formatos; this.fornadas = fornadas; this.apoio = apoio;
    }

    /** Bricks of one format in each state, as in the RESUMO of the factory's spreadsheet. */
    public record PorFormato(Formato formato, Map<StatusForno.Estado, Long> porEstado) {
        public long tijolos(StatusForno.Estado e) { return porEstado.getOrDefault(e, 0L); }
        public long total() { return porEstado.values().stream().mapToLong(Long::longValue).sum(); }
    }
    public record Resumo(long vazias, List<PorFormato> formatos) {
        public long tijolos(StatusForno.Estado e) { return formatos.stream().mapToLong(f -> f.tijolos(e)).sum(); }
        public long total() { return formatos.stream().mapToLong(PorFormato::total).sum(); }
    }
    /** Every active format appears, even at zero, so the summary always has the same rows. */
    public Resumo resumo(StatusForno s, List<Formato> ativos) {
        var porFormato = new LinkedHashMap<UUID, PorFormato>();
        for (var f : ativos) porFormato.put(f.getId(), new PorFormato(f, new EnumMap<>(StatusForno.Estado.class)));
        for (var l : s.getLinhas()) {
            if (l.isVazia()) continue;
            porFormato.computeIfAbsent(l.getFormato().getId(), id -> new PorFormato(l.getFormato(), new EnumMap<>(StatusForno.Estado.class)))
                .porEstado().merge(l.getEstado(), (long) l.getQuantidade(), Long::sum);
        }
        return new Resumo(s.getLinhas().stream().filter(StatusForno.Linha::isVazia).count(), List.copyOf(porFormato.values()));
    }

    /** Lines of one format: what the Enforna and Desenforna records leave in the kiln, against what the map shows. */
    public record LinhasPorFormato(Formato formato, int pelasFornadas, int noMapa) {
        public boolean bate() { return pelasFornadas == noMapa; }
    }
    /** The map checked against the loads in progress; it only warns, because either side may be the one behind. */
    public record Conferencia(List<Fornada> fornadas, List<LinhasPorFormato> formatos) {
        public int pelasFornadas() { return formatos.stream().mapToInt(LinhasPorFormato::pelasFornadas).sum(); }
        public int noMapa() { return formatos.stream().mapToInt(LinhasPorFormato::noMapa).sum(); }
        public boolean bate() { return formatos.stream().allMatch(LinhasPorFormato::bate); }
        public String fornadasNoForno() {
            return String.join(" e ", fornadas.stream().map(f -> f.getIdentificacao() + (f.isRecebendoEnforna() ? " (recebendo Enforna)" : " (desenfornando)")).toList());
        }
        public String mensagem() {
            String confira = " Confira se o papel do forneiro está atualizado ou se falta lançar alguma Enforna ou Desenforna.";
            if (pelasFornadas() != noMapa())
                return "As Enfornas e Desenfornas dizem " + linhas(pelasFornadas()) + " no forno, mas o mapa mostra " + noMapa() + "." + confira;
            if (!bate()) return "O total de " + linhas(noMapa()) + " bate, mas os formatos não." + confira;
            return "As Enfornas e Desenfornas dizem " + linhas(pelasFornadas()) + " no forno. O mapa mostra " + noMapa() + ". Está batendo.";
        }
        private static String linhas(int n) { return n + (n == 1 ? " linha" : " linhas"); }
    }
    @Transactional(readOnly = true)
    public Conferencia conferencia(StatusForno s, UsuarioAutenticado p) {
        exigirUnidade(apoio.usuarioAtivo(p), s.getUnidade().getId());
        var abertas = fornadas.emAndamento(s.getUnidade().getId());
        var vistos = new HashMap<UUID, Formato>(); var pelasFornadas = new HashMap<UUID, Integer>(); var noMapa = new HashMap<UUID, Integer>();
        for (var fornada : abertas)
            for (var b : fornadas.balanco(fornada).formatos()) {
                vistos.put(b.formato().getId(), b.formato());
                pelasFornadas.merge(b.formato().getId(), Math.max(0, b.linhasEnfornadas() - b.linhasDesenfornadas()), Integer::sum);
            }
        for (var l : s.getLinhas()) {
            if (l.isVazia()) continue;
            vistos.put(l.getFormato().getId(), l.getFormato());
            noMapa.merge(l.getFormato().getId(), 1, Integer::sum);
        }
        var porFormato = vistos.values().stream().sorted(Comparator.comparing(Formato::getNome))
            .map(f -> new LinhasPorFormato(f, pelasFornadas.getOrDefault(f.getId(), 0), noMapa.getOrDefault(f.getId(), 0))).toList();
        return new Conferencia(abertas, porFormato);
    }

    @Transactional(readOnly = true)
    public List<StatusForno> recentes(UUID unidade, UsuarioAutenticado p, int quantidade) {
        exigirUnidade(apoio.usuarioAtivo(p), unidade);
        return status.findByUnidadeIdOrderByDataHoraDescCriadoEmDesc(unidade, PageRequest.of(0, quantidade));
    }
    @Transactional(readOnly = true)
    public StatusForno consultar(UUID id, UsuarioAutenticado p) {
        var s = status.findById(id).orElseThrow(() -> new RegraNegocioException("Status do forno não encontrado."));
        exigirUnidade(apoio.usuarioAtivo(p), s.getUnidade().getId());
        return s;
    }
    @Transactional
    public StatusForno registrar(UsuarioAutenticado p, StatusFornoForm f) {
        var gestor = apoio.comPerfil(p, Perfil.GESTOR_PRODUCAO, Perfil.GESTOR_GERAL);
        var unidade = Optional.ofNullable(f.getIdUnidade()).flatMap(unidades::findById)
            .orElseThrow(() -> new RegraNegocioException("Escolha uma unidade cadastrada."));
        if (f.getDataHora() == null) throw new RegraNegocioException("Informe a data e a hora do status.");
        var quando = f.getDataHora().atZone(RegistroApoio.FUSO).toInstant();
        if (quando.isAfter(apoio.agora())) throw new RegraNegocioException("Confira a data e a hora: elas não podem estar no futuro.");
        var observacao = f.getObservacao() == null || f.getObservacao().isBlank() ? null : f.getObservacao().strip();
        if (observacao != null && observacao.length() > 300) throw new RegraNegocioException("A observação pode ter até 300 caracteres.");
        int total = unidade.getLinhasForno();
        if (f.getLinhas().keySet().stream().anyMatch(n -> n == null || n < 1 || n > total))
            throw new RegraNegocioException("O forno desta unidade tem " + total + " linhas.");
        var linhas = new ArrayList<StatusForno.Linha>();
        for (int n = 1; n <= total; n++) {
            var l = f.getLinhas().getOrDefault(n, new StatusFornoForm.Linha());
            if (l.getIdFormato() == null && l.getEstado() != null)
                throw new RegraNegocioException("Linha " + n + ": escolha o tijolo, ou deixe o status em branco se a linha estiver vazia.");
            if (l.getIdFormato() != null && l.getEstado() == null)
                throw new RegraNegocioException("Linha " + n + ": escolha o status do tijolo.");
            Formato formato = null;
            if (l.getIdFormato() != null)
                formato = formatos.findById(l.getIdFormato()).filter(Formato::isAtivo)
                    .orElseThrow(() -> new RegraNegocioException("Escolha um formato cadastrado."));
            linhas.add(new StatusForno.Linha(n, formato, l.getEstado()));
        }
        var salvo = status.saveAndFlush(new StatusForno(unidade, gestor, quando, observacao, linhas, apoio.agora()));
        apoio.auditar("status_forno", salvo.getId(), gestor, "CRIACAO", null, salvo.retrato(), null, null);
        return salvo;
    }
    /** Registro de Produção reads only the kiln of its own unit (RN05). */
    private void exigirUnidade(Usuario u, UUID unidade) {
        if (u.getPerfil() == Perfil.REGISTRO_PRODUCAO && !u.getUnidade().getId().equals(unidade)) apoio.negar();
    }
}
