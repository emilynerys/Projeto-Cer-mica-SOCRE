package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Maromba records; the operations shared with the furnace stages live in EtapaService.
@Service
public class ProducaoService extends EtapaService<Maromba> {
    public static final ZoneId FUSO = RegistroApoio.FUSO;
    private final MarombaRepository marombas; private final FormatoRepository formatos;
    public ProducaoService(MarombaRepository marombas, FormatoRepository formatos, RegistroApoio apoio) {
        super(marombas, apoio); this.marombas = marombas; this.formatos = formatos;
    }
    @Override protected String tabela() { return "maromba"; }
    public LocalDate hoje() { return apoio.hoje(); }
    public List<Formato> formatosAtivos() { return formatos.findByAtivoTrueOrderByNome(); }
    // Each active format with a quantity above zero becomes one record; all of them share the submission key.
    @Transactional
    public List<Maromba> criar(UsuarioAutenticado p, MarombaForm f) {
        var autor = apoio.comPerfil(p, Perfil.REGISTRO_PRODUCAO);
        var ativos = formatosAtivos();
        validarCriacao(f, ativos);
        apoio.conferirData(autor.getUnidade(), f.getData());
        apoio.travar(f.getChaveEnvio().toString());
        var texto = new StringBuilder(autor.getId() + "|" + autor.getUnidade().getId() + "|" + f.getData());
        new TreeMap<>(f.getQuantidades()).forEach((formato, q) -> texto.append('|').append(formato).append('=').append(q));
        String impressao = apoio.impressao(texto.toString());
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
            criados.add(marombas.saveAndFlush(new Maromba(autor.getUnidade(), formato, autor, f.getData(), quantidade, f.getChaveEnvio(), impressao, apoio.agora())));
        }
        for (var m : criados) apoio.auditar(m, autor, "CRIACAO", null);
        return criados;
    }
    // A correction changes one record, that is, one format, and needs a reason (RN15).
    @Transactional
    public Maromba editar(UUID id, UsuarioAutenticado p, MarombaForm f) {
        var c = iniciarCorrecao(id, p, f.getVersao(), f.getIdMotivo(), f.getTextoMotivo());
        var m = c.registro();
        apoio.conferirData(m.getUnidade(), f.getData());
        Integer quantidade = f.getQuantidades().get(m.getFormato().getId());
        if (quantidade == null || quantidade <= 0 || quantidade > 1000000000)
            throw new RegraNegocioException("Informe uma quantidade inteira maior que zero para " + m.getFormato().getNome() + ".");
        m.editar(f.getData(), quantidade);
        return concluirCorrecao(c);
    }
    private void validarCriacao(MarombaForm f, List<Formato> ativos) {
        if (f.getChaveEnvio() == null || f.getData() == null || f.getData().isAfter(hoje()))
            throw new RegraNegocioException("Confira a data: não é possível registrar uma produção futura.");
        var ids = ativos.stream().map(Formato::getId).toList();
        if (!f.getQuantidades().keySet().equals(new HashSet<>(ids)) || f.getQuantidades().values().stream()
                .anyMatch(q -> q == null || q < 0 || q > 1000000000))
            throw new RegraNegocioException("Preencha todos os formatos com números inteiros maiores ou iguais a zero.");
        if (f.getQuantidades().values().stream().allMatch(q -> q == 0))
            throw new RegraNegocioException("Informe ao menos uma quantidade maior que zero.");
    }
}
