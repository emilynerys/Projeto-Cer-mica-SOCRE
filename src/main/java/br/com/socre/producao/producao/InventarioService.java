package br.com.socre.producao.producao;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// RN20: the unit's starting point, informed once by the Administrador; records up to that day are refused afterwards.
@Service
public class InventarioService {
    private final InventarioRepository inventarios; private final FornadaRepository fornadas; private final UnidadeRepository unidades;
    private final FormatoRepository formatos; private final RegistroApoio apoio;
    public InventarioService(InventarioRepository inventarios, FornadaRepository fornadas, UnidadeRepository unidades,
            FormatoRepository formatos, RegistroApoio apoio) {
        this.inventarios = inventarios; this.fornadas = fornadas; this.unidades = unidades; this.formatos = formatos; this.apoio = apoio;
    }
    @Transactional(readOnly = true)
    public List<InventarioInicial> consultar(UUID unidade, UsuarioAutenticado p) {
        apoio.usuarioAtivo(p);
        return inventarios.findByUnidadeIdOrderByEstadoAscFormatoNomeAsc(unidade);
    }
    @Transactional
    public List<InventarioInicial> registrar(UsuarioAutenticado p, InventarioForm f) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        var unidade = unidades.findById(f.getIdUnidade()).orElseThrow(() -> new RegraNegocioException("Escolha uma unidade cadastrada."));
        apoio.travar("inventario-" + unidade.getId());
        if (inventarios.referencia(unidade.getId()).isPresent())
            throw new RegraNegocioException("O inventário inicial desta unidade já foi informado e não pode ser repetido.");
        if (f.getReferencia() == null || f.getReferencia().atZone(RegistroApoio.FUSO).toInstant().isAfter(apoio.agora()))
            throw new RegraNegocioException("Confira a data e a hora de referência: elas não podem estar no futuro.");
        if (f.getItens().isEmpty()) throw new RegraNegocioException("Informe o conteúdo do forno e o produto acabado.");
        var referencia = f.getReferencia().atZone(RegistroApoio.FUSO).toInstant();
        var cargas = new LinkedHashMap<String, Fornada>();
        var salvos = new ArrayList<InventarioInicial>();
        for (var i : f.getItens()) {
            var formato = formatos.findById(i.getIdFormato()).orElseThrow(() -> new RegraNegocioException("Escolha um formato cadastrado."));
            Fornada fornada = null;
            if (i.getEstado().noForno()) {
                if (i.getMesFornada() == null || i.getNumeroFornada() == null || i.getNumeroFornada() <= 0)
                    throw new RegraNegocioException("Informe o mês e o número da fornada do tijolo que está no forno.");
                if (i.getLinhas() == null || i.getLinhas() <= 0)
                    throw new RegraNegocioException("Informe as linhas do tijolo que está no forno.");
                fornada = cargas.computeIfAbsent(i.getMesFornada() + "/" + i.getNumeroFornada(),
                    k -> fornadas.saveAndFlush(new Fornada(unidade, i.getMesFornada(), i.getNumeroFornada())));
            } else if (i.getMesFornada() != null || i.getNumeroFornada() != null) {
                throw new RegraNegocioException("O produto acabado fica fora do forno e não tem fornada.");
            }
            salvos.add(inventarios.saveAndFlush(new InventarioInicial(unidade, fornada, formato, referencia, i.getEstado(),
                i.getEstado().noForno() ? i.getLinhas() : null, i.getQuantidade())));
        }
        if (cargas.size() > 2) throw new RegraNegocioException("O forno tem no máximo duas fornadas em andamento ao mesmo tempo.");
        for (var c : cargas.values()) apoio.auditar("fornada", c.getId(), admin, "IMPORTACAO", null, FornadaService.retrato(c), null, null);
        for (var s : salvos) apoio.auditar("inventario_inicial", s.getId(), admin, "IMPORTACAO", null, s.retrato(), null, null);
        return salvos;
    }
}
