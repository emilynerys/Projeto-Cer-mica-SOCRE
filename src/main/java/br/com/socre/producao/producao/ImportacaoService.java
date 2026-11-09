package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// RN22: the records made between the initial inventory and the start of use enter once, already Validated, all of them or none.
@Service
public class ImportacaoService {
    public static final String CABECALHO = "etapa;data;mes_fornada;numero_fornada;formato;linhas;tijolos_por_linha;quantidade;primeira;segunda;cru";
    private static final int COLUNAS = 11, LINHAS_MAXIMAS = 5000, ERROS_MOSTRADOS = 30;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
        MES = DateTimeFormatter.ofPattern("MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final MarombaRepository marombas; private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas;
    private final FornadaRepository fornadas; private final FormatoRepository formatos; private final UnidadeRepository unidades;
    private final InventarioRepository inventarios; private final RegistroApoio apoio;
    public ImportacaoService(MarombaRepository marombas, EnfornaRepository enfornas, DesenfornaRepository desenfornas, FornadaRepository fornadas,
            FormatoRepository formatos, UnidadeRepository unidades, InventarioRepository inventarios, RegistroApoio apoio) {
        this.marombas = marombas; this.enfornas = enfornas; this.desenfornas = desenfornas; this.fornadas = fornadas; this.formatos = formatos;
        this.unidades = unidades; this.inventarios = inventarios; this.apoio = apoio;
    }
    private enum Etapa { MAROMBA, ENFORNA, DESENFORNA }
    private record Linha(int numero, String texto, Etapa etapa, LocalDate data, YearMonth mes, Integer fornada, Formato formato,
            int linhas, int tijolosPorLinha, int quantidade, int primeira, int segunda, int cru) {}
    /** What the file would do; nothing is saved while there are errors. */
    public record Conferencia(int maromba, int enforna, int desenforna, List<String> fornadasNovas, List<String> erros, int errosOcultos) {
        public int total() { return maromba + enforna + desenforna; }
        public boolean podeImportar() { return erros.isEmpty() && total() > 0; }
    }
    public boolean jaImportado(UUID unidade) {
        return marombas.existeImportado(unidade) || enfornas.existeImportado(unidade) || desenfornas.existeImportado(unidade);
    }
    @Transactional(readOnly = true)
    public Conferencia conferir(UsuarioAutenticado p, UUID idUnidade, String texto) {
        apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        return ler(unidade(idUnidade), texto).conferencia;
    }
    @Transactional
    public Conferencia importar(UsuarioAutenticado p, UUID idUnidade, String texto) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        var unidade = unidade(idUnidade);
        apoio.travar("importacao-" + unidade.getId());
        var lido = ler(unidade, texto);
        if (!lido.conferencia.podeImportar()) return lido.conferencia;
        var agora = apoio.agora();
        var cargas = new HashMap<String, Fornada>();
        for (var l : lido.linhas) {
            RegistroEtapa r = switch (l.etapa()) {
                case MAROMBA -> new Maromba(unidade, l.formato(), admin, l.data(), l.quantidade(), UUID.randomUUID(), apoio.impressao(l.texto()), agora);
                case ENFORNA -> new Enforna(carga(cargas, unidade, l, admin), l.formato(), admin, l.data(), l.linhas(), l.tijolosPorLinha(), l.quantidade(),
                    UUID.randomUUID(), apoio.impressao(l.texto()), agora);
                case DESENFORNA -> new Desenforna(carga(cargas, unidade, l, admin), l.formato(), admin, l.data(), l.linhas(), l.primeira(), l.segunda(), l.cru(),
                    UUID.randomUUID(), apoio.impressao(l.texto()), agora);
            };
            r.importar(admin, agora);
            switch (r) {
                case Maromba m -> marombas.saveAndFlush(m);
                case Enforna e -> enfornas.saveAndFlush(e);
                case Desenforna d -> desenfornas.saveAndFlush(d);
                default -> throw new IllegalStateException();
            }
            apoio.auditar(r, admin, "IMPORTACAO", null);
        }
        fecharAntigas(unidade, admin, agora);
        return lido.conferencia;
    }
    /** RN08 allows two loads in progress: after the import only the two most recent stay open, and the Gestor closes what is left. */
    private void fecharAntigas(Unidade unidade, Usuario admin, Instant agora) {
        var abertas = fornadas.findByUnidadeIdAndSituacaoOrderByMesAscNumeroAsc(unidade.getId(), Fornada.Situacao.EM_ANDAMENTO);
        for (int i = 0; i < abertas.size(); i++) {
            var f = abertas.get(i);
            String antes = FornadaService.retrato(f);
            if (i < abertas.size() - 2) f.concluir(agora);
            else if (i == abertas.size() - 2 && f.isRecebendoEnforna()) f.registrar(Fornada.Marco.ENFORNA_CONCLUIDA, agora);
            else continue;
            fornadas.flush();
            apoio.auditar("fornada", f.getId(), admin, f.isEmAndamento() ? "MARCO" : "CONCLUSAO", antes, FornadaService.retrato(f), null, null);
        }
    }
    private Fornada carga(Map<String, Fornada> cargas, Unidade unidade, Linha l, Usuario admin) {
        return cargas.computeIfAbsent(l.mes() + "/" + l.fornada(), k -> fornadas.findByUnidadeIdAndMesAndNumero(unidade.getId(), l.mes().atDay(1), l.fornada()).orElseGet(() -> {
            var nova = fornadas.saveAndFlush(new Fornada(unidade, l.mes(), l.fornada()));
            apoio.auditar("fornada", nova.getId(), admin, "IMPORTACAO", null, FornadaService.retrato(nova), null, null);
            return nova;
        }));
    }
    private Unidade unidade(UUID id) {
        return Optional.ofNullable(id).flatMap(unidades::findById).orElseThrow(() -> new RegraNegocioException("Escolha uma unidade cadastrada."));
    }

    private record Lido(List<Linha> linhas, Conferencia conferencia) {}
    private Lido ler(Unidade unidade, String texto) {
        var erros = new ArrayList<String>();
        var linhas = new ArrayList<Linha>();
        if (jaImportado(unidade.getId())) erros.add("A importação desta unidade já foi feita e não pode ser repetida.");
        if (inventarios.referencia(unidade.getId()).isEmpty()) erros.add("Informe o inventário inicial da unidade antes de importar.");
        var todas = (texto == null ? "" : texto.replace("﻿", "")).split("\\R");
        if (todas.length == 0 || !CABECALHO.equalsIgnoreCase(todas[0].strip()))
            erros.add("A primeira linha precisa ser o cabeçalho: " + CABECALHO);
        else if (todas.length - 1 > LINHAS_MAXIMAS) erros.add("O arquivo pode ter até " + LINHAS_MAXIMAS + " linhas.");
        else {
            var vistos = new HashSet<String>();
            var porNome = new HashMap<String, Formato>();
            for (var f : formatos.findAll()) { porNome.put(f.getNome().toLowerCase(Locale.ROOT), f); porNome.put(f.getSigla().toLowerCase(Locale.ROOT), f); }
            for (int i = 1; i < todas.length; i++) {
                if (todas[i].isBlank()) continue;
                try {
                    var l = linha(i + 1, todas[i], porNome);
                    apoio.conferirData(unidade, l.data());
                    String chave = l.etapa() + "|" + l.mes() + "|" + l.fornada() + "|" + l.formato().getId() + "|" + l.data();
                    if (!vistos.add(chave)) throw new RegraNegocioException("esta etapa, formato e dia já apareceram antes no arquivo.");
                    conferirRepetido(unidade, l);
                    linhas.add(l);
                } catch (RegraNegocioException e) { erros.add("Linha " + (i + 1) + ": " + e.getMessage()); }
            }
            if (linhas.isEmpty() && erros.isEmpty()) erros.add("O arquivo não tem lançamentos.");
        }
        var novas = new TreeSet<String>();
        for (var l : linhas)
            if (l.fornada() != null && fornadas.findByUnidadeIdAndMesAndNumero(unidade.getId(), l.mes().atDay(1), l.fornada()).isEmpty())
                novas.add(new Fornada(unidade, l.mes(), l.fornada()).getIdentificacao());
        int m = 0, e = 0, d = 0;
        for (var l : linhas) switch (l.etapa()) { case MAROMBA -> m++; case ENFORNA -> e++; case DESENFORNA -> d++; }
        int ocultos = Math.max(0, erros.size() - ERROS_MOSTRADOS);
        return new Lido(linhas, new Conferencia(m, e, d, List.copyOf(novas), List.copyOf(erros.subList(0, erros.size() - ocultos)), ocultos));
    }
    /** RN09: one active record per format and day; the file does not overwrite what was already typed. */
    private void conferirRepetido(Unidade unidade, Linha l) {
        boolean existe = switch (l.etapa()) {
            case MAROMBA -> marombas.existeAtivo(unidade.getId(), l.formato().getId(), l.data());
            case ENFORNA -> fornadas.findByUnidadeIdAndMesAndNumero(unidade.getId(), l.mes().atDay(1), l.fornada())
                .filter(f -> enfornas.existeAtivo(f.getId(), l.formato().getId(), l.data())).isPresent();
            case DESENFORNA -> fornadas.findByUnidadeIdAndMesAndNumero(unidade.getId(), l.mes().atDay(1), l.fornada())
                .filter(f -> desenfornas.existeAtivo(f.getId(), l.formato().getId(), l.data())).isPresent();
        };
        if (existe) throw new RegraNegocioException("já existe um lançamento desta etapa, formato e dia no sistema.");
    }
    private static Linha linha(int numero, String texto, Map<String, Formato> porNome) {
        var c = texto.split(";", -1);
        if (c.length != COLUNAS) throw new RegraNegocioException("são " + COLUNAS + " colunas separadas por ponto e vírgula; esta linha tem " + c.length + ".");
        Etapa etapa;
        try { etapa = Etapa.valueOf(c[0].strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException x) { throw new RegraNegocioException("a etapa precisa ser MAROMBA, ENFORNA ou DESENFORNA."); }
        LocalDate data;
        try { data = LocalDate.parse(c[1].strip(), DATA); }
        catch (DateTimeParseException x) { throw new RegraNegocioException("a data precisa estar como 14/08/2026."); }
        var formato = porNome.get(c[4].strip().toLowerCase(Locale.ROOT));
        if (formato == null) throw new RegraNegocioException("formato “" + c[4].strip() + "” não está cadastrado.");
        if (etapa == Etapa.MAROMBA) {
            int quantidade = numero(c[7], "quantidade");
            if (quantidade <= 0) throw new RegraNegocioException("informe a quantidade da Maromba.");
            return new Linha(numero, texto, etapa, data, null, null, formato, 0, 0, quantidade, 0, 0, 0);
        }
        YearMonth mes;
        try { mes = YearMonth.parse(c[2].strip(), MES); }
        catch (DateTimeParseException x) { throw new RegraNegocioException("o mês da fornada precisa estar como 08/2026."); }
        int fornada = numero(c[3], "número da fornada"), linhas = numero(c[5], "linhas");
        if (fornada <= 0) throw new RegraNegocioException("informe o número da fornada.");
        if (linhas <= 0) throw new RegraNegocioException("informe as linhas.");
        if (etapa == Etapa.ENFORNA) {
            int porLinha = c[6].isBlank() ? formato.getTijolosPorLinha() : numero(c[6], "tijolos por linha");
            if (porLinha <= 0) throw new RegraNegocioException("informe os tijolos por linha.");
            int quantidade;
            try { quantidade = c[7].isBlank() ? Math.multiplyExact(linhas, porLinha) : numero(c[7], "quantidade"); }
            catch (ArithmeticException x) { throw new RegraNegocioException("a quantidade é grande demais."); }
            if (quantidade <= 0) throw new RegraNegocioException("informe a quantidade da Enforna.");
            return new Linha(numero, texto, etapa, data, mes, fornada, formato, linhas, porLinha, quantidade, 0, 0, 0);
        }
        int primeira = numero(c[8], "1ª qualidade"), segunda = numero(c[9], "2ª qualidade"), cru = numero(c[10], "cru");
        if ((long) primeira + segunda + cru <= 0 || (long) primeira + segunda + cru > Integer.MAX_VALUE)
            throw new RegraNegocioException("informe a 1ª, a 2ª ou o cru da Desenforna.");
        return new Linha(numero, texto, etapa, data, mes, fornada, formato, linhas, 0, 0, primeira, segunda, cru);
    }
    /** Blank is zero; "16.100" is accepted because spreadsheets write it that way. */
    private static int numero(String valor, String nome) {
        var v = valor.strip().replace(".", "");
        if (v.isEmpty()) return 0;
        try {
            int n = Integer.parseInt(v);
            if (n < 0) throw new RegraNegocioException(nome + " não pode ser negativo.");
            return n;
        } catch (NumberFormatException x) { throw new RegraNegocioException(nome + " precisa ser um número inteiro."); }
    }
}
