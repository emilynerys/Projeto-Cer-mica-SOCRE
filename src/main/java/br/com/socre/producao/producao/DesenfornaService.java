package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.text.NumberFormat;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Desenforna: bricks taken out of a furnace load, one record per format and day (RN09, RN10, RN14).
@Service
public class DesenfornaService extends EtapaService<Desenforna> {
    private final DesenfornaRepository desenfornas; private final FornadaRepository fornadas; private final FornadaService fornadaService;
    private final FormatoRepository formatos;
    public DesenfornaService(DesenfornaRepository desenfornas, FornadaRepository fornadas, FornadaService fornadaService,
            FormatoRepository formatos, RegistroApoio apoio) {
        super(desenfornas, apoio);
        this.desenfornas = desenfornas; this.fornadas = fornadas; this.fornadaService = fornadaService; this.formatos = formatos;
    }
    @Override protected String tabela() { return "desenforna"; }
    record Linha(Formato formato, int linhas, int primeira, int segunda, int cru) { int total() { return primeira + segunda + cru; } }

    @Transactional
    public List<Desenforna> criar(UsuarioAutenticado p, DesenfornaForm f) {
        var autor = apoio.comPerfil(p, Perfil.REGISTRO_PRODUCAO);
        var unidade = autor.getUnidade();
        if (f.getChaveEnvio() == null) throw new RegraNegocioException("Abra novamente o formulário para gerar um identificador de envio.");
        apoio.conferirData(unidade, f.getData());
        var fornada = f.getIdFornada() == null ? null : fornadas.findById(f.getIdFornada()).orElse(null);
        if (fornada == null || !fornada.getUnidade().getId().equals(unidade.getId()) || !fornada.isEmAndamento())
            throw new RegraNegocioException("Escolha a fornada em andamento de onde o tijolo saiu.");
        var linhas = new ArrayList<Linha>();
        for (var formato : formatos.findByAtivoTrueOrderByNome()) {
            var l = linha(formato, f.getItens().get(formato.getId()));
            if (l != null) linhas.add(l);
        }
        if (linhas.isEmpty()) throw new RegraNegocioException("Informe ao menos um formato com tijolos desenfornados.");
        apoio.travar(f.getChaveEnvio().toString());
        var texto = new StringBuilder(autor.getId() + "|" + f.getData() + "|" + fornada.getId());
        linhas.forEach(l -> texto.append('|').append(l));
        String impressao = apoio.impressao(texto.toString());
        var anteriores = desenfornas.findByChaveEnvioOrderByFormatoNome(f.getChaveEnvio());
        if (!anteriores.isEmpty()) {
            if (anteriores.stream().anyMatch(d -> !d.getAutor().getId().equals(autor.getId()) || !d.getImpressaoEnvio().equals(impressao)))
                throw new RegraNegocioException("Este envio já foi usado com outros dados. Consulte seus registros e abra um novo formulário.");
            return anteriores;
        }
        if (!f.isConfirmarAvisos()) {
            var avisos = avisos(linhas, fornada, 0);
            if (!avisos.isEmpty()) throw new AvisosPendentesException(avisos);
        }
        var criados = new ArrayList<Desenforna>();
        for (var l : linhas) {
            if (desenfornas.existeAtivo(fornada.getId(), l.formato().getId(), f.getData()))
                throw new RegraNegocioException("Já existe Desenforna de " + l.formato().getNome() + " nesta data e fornada. Consulte o registro para conferir ou corrigir.");
            criados.add(desenfornas.saveAndFlush(new Desenforna(fornada, l.formato(), autor, f.getData(), l.linhas(), l.primeira(),
                l.segunda(), l.cru(), f.getChaveEnvio(), impressao, apoio.agora())));
        }
        for (var d : criados) apoio.auditar(d, autor, "CRIACAO", null);
        return criados;
    }
    @Transactional
    public Desenforna editar(UUID id, UsuarioAutenticado p, DesenfornaForm f) {
        var c = iniciarCorrecao(id, p, f.getVersao(), f.getIdMotivo(), f.getTextoMotivo());
        var d = c.registro();
        apoio.conferirData(d.getUnidade(), f.getData());
        var l = linha(d.getFormato(), f.getItens().get(d.getFormato().getId()));
        if (l == null) throw new RegraNegocioException("Informe a Desenforna de " + d.getFormato().getNome() + ". Para apagar o registro, use o cancelamento.");
        if (!f.isConfirmarAvisos()) {
            var avisos = avisos(List.of(l), d.getFornada(), d.getLinhas());
            if (!avisos.isEmpty()) throw new AvisosPendentesException(avisos);
        }
        d.editar(f.getData(), l.linhas(), l.primeira(), l.segunda(), l.cru());
        return concluirCorrecao(c);
    }
    /** RN14 warnings of a saved record, shown to the Gestor during validation. */
    @Transactional(readOnly = true)
    public List<String> avisos(Desenforna d) {
        return avisos(List.of(new Linha(d.getFormato(), d.getLinhas(), d.getPrimeira(), d.getSegunda(), d.getCru())), d.getFornada(), d.getLinhas());
    }

    private Linha linha(Formato formato, DesenfornaForm.Item item) {
        if (item == null) return null;
        int linhas = valor(item.getLinhas()), primeira = valor(item.getPrimeira()), segunda = valor(item.getSegunda()), cru = valor(item.getCru());
        if (linhas == 0 && primeira + segunda + cru == 0) return null;
        if (linhas <= 0) throw new RegraNegocioException("Informe as linhas desenfornadas de " + formato.getNome() + ".");
        if (primeira + segunda + cru <= 0) throw new RegraNegocioException("Informe os tijolos desenfornados de " + formato.getNome() + ".");
        return new Linha(formato, linhas, primeira, segunda, cru);
    }
    private static int valor(Integer v) { return v == null ? 0 : v; }
    /** RN14: more bricks than lines × reference, or more lines taken out than were put into the load. Never blocks. */
    private List<String> avisos(List<Linha> linhas, Fornada fornada, int linhasJaContadas) {
        var avisos = new ArrayList<String>();
        var n = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));
        for (var l : linhas) {
            long esperado = (long) l.linhas() * l.formato().getTijolosPorLinha();
            if (l.total() > esperado)
                avisos.add(l.formato().getNome() + ": " + n.format(l.total()) + " tijolos (1ª + 2ª + Cru) é mais do que " + l.linhas()
                    + " linhas × " + n.format(l.formato().getTijolosPorLinha()) + " = " + n.format(esperado) + ".");
        }
        var balanco = fornadaService.balanco(fornada);
        int desenfornadas = balanco.linhasDesenfornadas() - linhasJaContadas + linhas.stream().mapToInt(Linha::linhas).sum();
        if (desenfornadas > balanco.linhasEnfornadas())
            avisos.add("A " + fornada.getIdentificacao() + " ficaria com " + desenfornadas + " linhas desenfornadas, mais do que as "
                + balanco.linhasEnfornadas() + " linhas enfornadas.");
        return avisos;
    }
}
