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
    private final EnfornaRepository enfornas; private final FornadaRepository fornadas; private final FornadaService fornadaService;
    private final FormatoRepository formatos; private final InventarioRepository inventarios;
    public EnfornaService(EnfornaRepository enfornas, FornadaRepository fornadas, FornadaService fornadaService,
            FormatoRepository formatos, InventarioRepository inventarios, RegistroApoio apoio) {
        super(enfornas, apoio);
        this.enfornas = enfornas; this.fornadas = fornadas; this.fornadaService = fornadaService; this.formatos = formatos; this.inventarios = inventarios;
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
        var texto = new StringBuilder(autor.getId() + "|" + f.getData() + "|" + (f.isNovaFornada() ? "nova" : f.getIdFornada()));
        linhas.forEach(l -> texto.append('|').append(l));
        String impressao = apoio.impressao(texto.toString());
        var anteriores = enfornas.findByChaveEnvioOrderByFormatoNome(f.getChaveEnvio());
        if (!anteriores.isEmpty()) {
            if (anteriores.stream().anyMatch(e -> !e.getAutor().getId().equals(autor.getId()) || !e.getImpressaoEnvio().equals(impressao)))
                throw new RegraNegocioException("Este envio já foi usado com outros dados. Consulte seus registros e abra um novo formulário.");
            return anteriores;
        }
        Fornada fornada = null;
        if (!f.isNovaFornada()) {
            fornada = f.getIdFornada() == null ? null : fornadas.findById(f.getIdFornada()).orElse(null);
            if (fornada == null || !fornada.getUnidade().getId().equals(unidade.getId()))
                throw new RegraNegocioException("Escolha a fornada que está recebendo Enforna ou \"Nova fornada\".");
            if (!fornada.isRecebendoEnforna())
                throw new RegraNegocioException("A " + fornada.getIdentificacao() + " não recebe mais Enforna. Escolha outra fornada.");
        } else if (!fornadaService.podeAbrirNova(unidade.getId())) {
            throw new RegraNegocioException("Não é possível começar uma nova fornada agora: ainda há fornada recebendo Enforna ou já há duas em andamento.");
        }
        if (!f.isConfirmarAvisos()) {
            var avisos = avisos(linhas, fornada, unidade, 0);
            if (!avisos.isEmpty()) throw new AvisosPendentesException(avisos);
        }
        if (fornada == null) fornada = fornadaService.abrirNova(unidade, autor);
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
    /** RN14: more bricks than lines × reference, or more lines than the useful capacity of the kiln. Never blocks. */
    private List<String> avisos(List<Linha> linhas, Fornada fornada, Unidade unidade, int linhasJaContadas) {
        var avisos = new ArrayList<String>();
        var n = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));
        for (var l : linhas) {
            long esperado = (long) l.linhas() * l.formato().getTijolosPorLinha();
            if (l.quantidade() > esperado)
                avisos.add(l.formato().getNome() + ": " + n.format(l.quantidade()) + " tijolos é mais do que " + l.linhas() + " linhas × "
                    + n.format(l.formato().getTijolosPorLinha()) + " = " + n.format(esperado) + ".");
        }
        int novas = linhas.stream().mapToInt(Linha::linhas).sum() - linhasJaContadas;
        int atuais = 0;
        if (fornada != null) {
            atuais += enfornas.daFornada(fornada.getId()).stream().mapToInt(Enforna::getLinhas).sum();
            atuais += inventarios.findByFornadaId(fornada.getId()).stream().mapToInt(i -> i.getLinhas() == null ? 0 : i.getLinhas()).sum();
        }
        // Line 1 is the firebox, so the useful capacity is one line less than the kiln size.
        int uteis = unidade.getLinhasForno() - 1;
        if (atuais + novas > uteis)
            avisos.add("A fornada ficaria com " + (atuais + novas) + " linhas enfornadas, mais do que as " + uteis + " linhas úteis do forno.");
        return avisos;
    }
}
