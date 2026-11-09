package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Burnt brick stock: the yard comes from the Gestor's count; the kiln is the Assado lines of the last map, counted as 1st quality.
@Service
public class EstoqueService {
    private final EstoquePatioRepository contagens; private final StatusFornoRepository mapas; private final FormatoRepository formatos;
    private final UnidadeRepository unidades; private final RegistroApoio apoio;
    public EstoqueService(EstoquePatioRepository contagens, StatusFornoRepository mapas, FormatoRepository formatos, UnidadeRepository unidades, RegistroApoio apoio) {
        this.contagens = contagens; this.mapas = mapas; this.formatos = formatos; this.unidades = unidades; this.apoio = apoio;
    }
    /** One format of the sheet; the yard values are null while nothing was counted. */
    public record Linha(Formato formato, Integer patioPrimeira, Integer patioSegunda, long forno) {
        public long totalPrimeira() { return (patioPrimeira == null ? 0 : patioPrimeira) + forno; }
    }
    public record Quadro(EstoquePatio contagem, StatusForno mapa, List<Linha> linhas) {}

    @Transactional(readOnly = true)
    public Quadro quadro(UUID unidade) {
        var contagem = contagens.findByUnidadeIdOrderByDataDescCriadoEmDesc(unidade, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        var mapa = mapas.findByUnidadeIdOrderByDataHoraDescCriadoEmDesc(unidade, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        var noForno = new HashMap<UUID, Long>();
        var lista = new ArrayList<>(formatos.findByAtivoTrueOrderByNome());
        if (mapa != null)
            for (var l : mapa.getLinhas())
                if (!l.isVazia() && l.getEstado() == StatusForno.Estado.ASSADO) {
                    noForno.merge(l.getFormato().getId(), (long) l.getQuantidade(), Long::sum);
                    if (lista.stream().noneMatch(f -> f.getId().equals(l.getFormato().getId()))) lista.add(l.getFormato());
                }
        if (contagem != null)
            for (var i : contagem.getItens()) if (lista.stream().noneMatch(f -> f.getId().equals(i.getFormato().getId()))) lista.add(i.getFormato());
        var linhas = lista.stream().map(f -> {
            var item = contagem == null ? null : contagem.item(f.getId()).orElse(null);
            return new Linha(f, item == null ? null : item.getPrimeira(), item == null ? null : item.getSegunda(), noForno.getOrDefault(f.getId(), 0L));
        }).toList();
        return new Quadro(contagem, mapa, linhas);
    }
    @Transactional(readOnly = true)
    public List<EstoquePatio> historico(UUID unidade, int quantidade) {
        return contagens.findByUnidadeIdOrderByDataDescCriadoEmDesc(unidade, PageRequest.of(0, quantidade));
    }
    @Transactional
    public EstoquePatio registrar(UsuarioAutenticado p, UUID idUnidade, LocalDate data, Map<UUID, Integer> primeira, Map<UUID, Integer> segunda) {
        var gestor = apoio.comPerfil(p, Perfil.GESTOR_PRODUCAO);
        var unidade = Optional.ofNullable(idUnidade).flatMap(unidades::findById).orElseThrow(() -> new RegraNegocioException("Escolha uma unidade cadastrada."));
        if (data == null || data.isAfter(apoio.hoje())) throw new RegraNegocioException("Confira a data da contagem: ela não pode estar no futuro.");
        var itens = new ArrayList<EstoquePatio.Item>();
        for (var f : formatos.findByAtivoTrueOrderByNome()) {
            int a = valor(primeira.get(f.getId()), f, "1ª"), b = valor(segunda.get(f.getId()), f, "2ª");
            itens.add(new EstoquePatio.Item(f, a, b));
        }
        var salvo = contagens.saveAndFlush(new EstoquePatio(unidade, gestor, data, itens, apoio.agora()));
        apoio.auditar("estoque_patio", salvo.getId(), gestor, "CRIACAO", null, salvo.retrato(), null, null);
        return salvo;
    }
    private static int valor(Integer v, Formato f, String qualidade) {
        if (v == null) return 0;
        if (v < 0 || v > 100_000_000) throw new RegraNegocioException("Confira a quantidade de " + f.getNome() + " " + qualidade + ": use um número inteiro, sem negativos.");
        return v;
    }
}
