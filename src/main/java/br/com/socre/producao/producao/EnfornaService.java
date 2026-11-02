package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.text.NumberFormat;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Enforna: bricks put into a furnace load, one record per format and day (RN09, RN12, RN14).
@Service
public class EnfornaService extends EtapaService<Enforna> {
    private final EnfornaRepository enfornas; private final FornadaService fornadaService;
    private final FormatoRepository formatos; private final InventarioRepository inventarios;
    public EnfornaService(EnfornaRepository enfornas, FornadaService fornadaService,
            FormatoRepository formatos, InventarioRepository inventarios, RegistroApoio apoio) {
        super(enfornas, apoio);
        this.enfornas = enfornas; this.fornadaService = fornadaService; this.formatos = formatos; this.inventarios = inventarios;
    }
    @Override protected String tabela() { return "enforna"; }
    record Linha(Formato formato, int linhas, int tijolosPorLinha, int quantidade) {}

    @Transactional
    public List<Enforna> criar(UsuarioAutenticado p, EnfornaForm f) {
        var autor = apoio.comPerfil(p, Perfil.REGISTRO_PRODUCAO);
        var unidade = autor.getUnidade();
        if (f.getChaveEnvio() == null) throw new RegraNegocioException("Abra novamente o formulário para gerar um identificador de envio.");
        apoio.conferirData(unidade, f.getData());
        var linhas = new ArrayList<Linha>();
        for (var formato : formatos.findByAtivoTrueOrderByNome()) {
            var l = linha(formato, f.getItens().get(formato.getId()));
            if (l != null) linhas.add(l);
        }
        if (linhas.isEmpty()) throw new RegraNegocioException("Informe ao menos um formato com linhas enfornadas.");
        apoio.travar(f.getChaveEnvio().toString());
        var texto = new StringBuilder(autor.getId() + "|" + f.getData() + "|" + f.getIdFornada());
        linhas.forEach(l -> texto.append('|').append(l));
        String impressao = apoio.impressao(texto.toString());
        var anteriores = enfornas.findByChaveEnvioOrderByFormatoNome(f.getChaveEnvio());
        if (!anteriores.isEmpty()) {
            if (anteriores.stream().anyMatch(e -> !e.getAutor().getId().equals(autor.getId()) || !e.getImpressaoEnvio().equals(impressao)))
                throw new RegraNegocioException("Este envio já foi usado com outros dados. Consulte seus registros e abra um novo formulário.");
            return anteriores;
        }
        // RN08: Enforna always goes to the load the Gestor opened last; the form says which one it showed.
        var fornada = fornadaService.paraEnforna(unidade.getId()).orElseThrow(() ->
            new RegraNegocioException("Nenhuma fornada aberta para Enforna. Peça ao Gestor de Produção para abrir a fornada na tela Fornadas."));
        if (f.getIdFornada() != null && !f.getIdFornada().equals(fornada.getId()))
            throw new RegraNegocioException("O Gestor de Produção abriu a " + fornada.getIdentificacao() + " enquanto você preenchia. Confira e envie de novo.");
        conferirCapacidade(linhas, fornada, unidade, 0);
        if (!f.isConfirmarAvisos()) {
            var avisos = avisos(linhas, fornada, unidade, 0);
            if (!avisos.isEmpty()) throw new AvisosPendentesException(avisos);
        }
        var criados = new ArrayList<Enforna>();
        for (var l : linhas) {
            if (enfornas.existeAtivo(fornada.getId(), l.formato().getId(), f.getData()))
                throw new RegraNegocioException("Já existe Enforna de " + l.formato().getNome() + " nesta data e fornada. Consulte o registro para conferir ou corrigir.");
            criados.add(enfornas.saveAndFlush(new Enforna(fornada, l.formato(), autor, f.getData(), l.linhas(), l.tijolosPorLinha(),
                l.quantidade(), f.getChaveEnvio(), impressao, apoio.agora())));
        }
        for (var e : criados) apoio.auditar(e, autor, "CRIACAO", null);
        return criados;
    }
    @Transactional
    public Enforna editar(UUID id, UsuarioAutenticado p, EnfornaForm f) {
        var c = iniciarCorrecao(id, p, f.getVersao(), f.getIdMotivo(), f.getTextoMotivo());
        var e = c.registro();
        apoio.conferirData(e.getUnidade(), f.getData());
        var l = linha(e.getFormato(), f.getItens().get(e.getFormato().getId()));
        if (l == null) throw new RegraNegocioException("Informe as linhas enfornadas de " + e.getFormato().getNome() + ". Para apagar o registro, use o cancelamento.");
        conferirCapacidade(List.of(l), e.getFornada(), e.getUnidade(), e.getLinhas());
        if (!f.isConfirmarAvisos()) {
            var avisos = avisos(List.of(l), e.getFornada(), e.getUnidade(), e.getLinhas());
            if (!avisos.isEmpty()) throw new AvisosPendentesException(avisos);
        }
        e.editar(f.getData(), l.linhas(), l.tijolosPorLinha(), l.quantidade());
        return concluirCorrecao(c);
    }
    /** RN14 warnings of a saved record, shown to the Gestor during validation. */
    @Transactional(readOnly = true)
    public List<String> avisos(Enforna e) {
        return avisos(List.of(new Linha(e.getFormato(), e.getLinhas(), e.getTijolosPorLinha(), e.getQuantidade())), e.getFornada(), e.getUnidade(), e.getLinhas());
    }

    private Linha linha(Formato formato, EnfornaForm.Item item) {
        if (item == null) return null;
        int linhas = item.getLinhas() == null ? 0 : item.getLinhas();
        int quantidade = item.getQuantidade() == null ? 0 : item.getQuantidade();
        if (linhas == 0 && quantidade == 0) return null;
        if (linhas <= 0) throw new RegraNegocioException("Informe as linhas enfornadas de " + formato.getNome() + ".");
        int porLinha = item.getTijolosPorLinha() == null ? formato.getTijolosPorLinha() : item.getTijolosPorLinha();
        if (porLinha <= 0) throw new RegraNegocioException("Informe os tijolos por linha de " + formato.getNome() + ".");
        // RN12: when the total is left blank, it is the suggestion lines × bricks per line.
        if (item.getQuantidade() == null) quantidade = Math.multiplyExact(linhas, porLinha);
        if (quantidade <= 0) throw new RegraNegocioException("Informe a quantidade de tijolos de " + formato.getNome() + ".");
        return new Linha(formato, linhas, porLinha, quantidade);
    }
    /** Lines the kiln holds: two of them always stay empty (field visit of 06/10/2026). */
    public static int capacidade(Unidade unidade) { return unidade.getLinhasForno() - 2; }
    /** Lines already in the load: initial inventory plus Enforna not cancelled. */
    public int linhasOcupadas(Fornada fornada) {
        if (fornada == null) return 0;
        return enfornas.daFornada(fornada.getId()).stream().mapToInt(Enforna::getLinhas).sum()
            + inventarios.findByFornadaId(fornada.getId()).stream().mapToInt(i -> i.getLinhas() == null ? 0 : i.getLinhas()).sum();
    }
    /** RN14: the load cannot pass the kiln capacity, adding all formats; a correction that does not add lines is always accepted. */
    private void conferirCapacidade(List<Linha> linhas, Fornada fornada, Unidade unidade, int linhasJaContadas) {
        int novas = linhas.stream().mapToInt(Linha::linhas).sum() - linhasJaContadas;
        int ocupadas = linhasOcupadas(fornada), limite = capacidade(unidade);
        if (novas > 0 && ocupadas + novas > limite)
            throw new RegraNegocioException("O forno comporta " + limite + " linhas (" + unidade.getLinhasForno() + " menos as 2 que ficam vagas). "
                + (fornada == null ? "" : "A " + fornada.getIdentificacao() + " já tem " + ocupadas + ". ")
                + "Ainda cabem " + Math.max(0, limite - ocupadas) + " linhas, somando todos os formatos.");
    }
    /** RN14: more bricks than lines × reference ask for confirmation; a saved load above the capacity is shown to the Gestor. */
    private List<String> avisos(List<Linha> linhas, Fornada fornada, Unidade unidade, int linhasJaContadas) {
        var avisos = new ArrayList<String>();
        var n = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));
        for (var l : linhas) {
            long esperado = (long) l.linhas() * l.formato().getTijolosPorLinha();
            if (l.quantidade() > esperado)
                avisos.add(l.formato().getNome() + ": " + n.format(l.quantidade()) + " tijolos é mais do que " + l.linhas() + " linhas × "
                    + n.format(l.formato().getTijolosPorLinha()) + " = " + n.format(esperado) + ".");
        }
        int total = linhasOcupadas(fornada) + linhas.stream().mapToInt(Linha::linhas).sum() - linhasJaContadas;
        if (total > capacidade(unidade))
            avisos.add("A fornada ficaria com " + total + " linhas enfornadas, mais do que as " + capacidade(unidade) + " que o forno comporta.");
        return avisos;
    }
}
