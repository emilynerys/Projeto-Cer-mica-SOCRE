package br.com.socre.producao.producao;

import br.com.socre.producao.cadastro.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// What is open, closed and in progress in the kiln, for the warnings of each profile.
@Service
public class PanoramaService {
    private final FornadaService fornadas; private final StatusFornoRepository mapas; private final UnidadeRepository unidades;
    private final MarombaRepository marombas; private final EnfornaRepository enfornas; private final DesenfornaRepository desenfornas;
    public PanoramaService(FornadaService fornadas, StatusFornoRepository mapas, UnidadeRepository unidades, MarombaRepository marombas,
            EnfornaRepository enfornas, DesenfornaRepository desenfornas) {
        this.fornadas = fornadas; this.mapas = mapas; this.unidades = unidades; this.marombas = marombas; this.enfornas = enfornas; this.desenfornas = desenfornas;
    }
    /** A load in progress: the newest one takes the Enforna; the older one is being emptied. */
    public record Aberta(Fornada fornada, boolean recebendoEnforna, boolean podeFechar) {}
    public record Panorama(Unidade unidade, List<Aberta> abertas, Fornada ultimaFechada, String fase, Instant mapaEm, long pendentes) {
        public Aberta paraEnforna() { return abertas.stream().filter(Aberta::recebendoEnforna).reduce((a, b) -> b).orElse(null); }
        public Aberta desenfornando() { return abertas.stream().filter(a -> !a.recebendoEnforna()).findFirst().orElse(abertas.isEmpty() ? null : abertas.getFirst()); }
    }
    @Transactional(readOnly = true)
    public Panorama de(UUID unidadeDoUsuario) {
        var unidade = unidades.deTrabalho(unidadeDoUsuario);
        var abertas = fornadas.emAndamento(unidade.getId()).stream()
            .map(f -> new Aberta(f, f.isRecebendoEnforna(), fornadas.balanco(f).sugerirConclusao())).toList();
        var ultima = fornadas.recentes(unidade.getId(), 10).stream().filter(f -> !f.isEmAndamento()).findFirst().orElse(null);
        var mapa = mapas.findByUnidadeIdOrderByDataHoraDescCriadoEmDesc(unidade.getId(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        long pendentes = marombas.pendentes(unidade.getId()) + enfornas.pendentes(unidade.getId()) + desenfornas.pendentes(unidade.getId());
        return new Panorama(unidade, abertas, ultima, fase(mapa), mapa == null ? null : mapa.getDataHora(), pendentes);
    }
    /** Read from the last kiln map: where the fire is burning, or whether it is still heating. */
    private static String fase(StatusForno mapa) {
        if (mapa == null) return "Nenhum Status do Forno lançado ainda";
        var queimando = mapa.getLinhas().stream().filter(l -> l.getEstado() == StatusForno.Estado.QUEIMA).mapToInt(StatusForno.Linha::getLinha).max();
        if (queimando.isPresent()) return "Queimando na linha " + queimando.getAsInt();
        if (mapa.getLinhas().stream().anyMatch(l -> l.getEstado() == StatusForno.Estado.ESQUENTE)) return "Em esquente";
        if (mapa.getLinhas().stream().anyMatch(l -> l.getEstado() == StatusForno.Estado.CRU)) return "Tijolo cru esperando o fogo";
        return "Sem fogo no forno";
    }
}
