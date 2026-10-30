package br.com.socre.producao.web;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.cadastro.*;
import br.com.socre.producao.producao.*;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// RN20: the Administrador informs, once per unit, what was in the kiln and in stock when the system starts.
@Controller @RequestMapping("/admin/inventario")
public class InventarioController {
    private final InventarioService inventario; private final UnidadeRepository unidades; private final ProducaoService producao;
    public InventarioController(InventarioService inventario, UnidadeRepository unidades, ProducaoService producao) {
        this.inventario = inventario; this.unidades = unidades; this.producao = producao;
    }

    /** The screen groups the items the way the factory counts them: up to two loads in the kiln, then finished product. */
    public static class Tela {
        private UUID idUnidade;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime referencia;
        private List<Carga> cargas = new ArrayList<>(List.of(new Carga(), new Carga()));
        private Map<UUID, Integer> primeira = new LinkedHashMap<>(), segunda = new LinkedHashMap<>();
        public static class Carga {
            @DateTimeFormat(pattern = "yyyy-MM") private YearMonth mes;
            private Integer numero;
            private InventarioInicial.Estado estado = InventarioInicial.Estado.AGUARDANDO_QUEIMA;
            private Map<UUID, Linha> itens = new LinkedHashMap<>();
            public YearMonth getMes() { return mes; } public void setMes(YearMonth v) { mes = v; }
            public Integer getNumero() { return numero; } public void setNumero(Integer v) { numero = v; }
            public InventarioInicial.Estado getEstado() { return estado; } public void setEstado(InventarioInicial.Estado v) { estado = v; }
            public Map<UUID, Linha> getItens() { return itens; } public void setItens(Map<UUID, Linha> v) { itens = v; }
        }
        public static class Linha {
            private Integer linhas, quantidade;
            public Integer getLinhas() { return linhas; } public void setLinhas(Integer v) { linhas = v; }
            public Integer getQuantidade() { return quantidade; } public void setQuantidade(Integer v) { quantidade = v; }
        }
        public UUID getIdUnidade() { return idUnidade; } public void setIdUnidade(UUID v) { idUnidade = v; }
        public LocalDateTime getReferencia() { return referencia; } public void setReferencia(LocalDateTime v) { referencia = v; }
        public List<Carga> getCargas() { return cargas; } public void setCargas(List<Carga> v) { cargas = v; }
        public Map<UUID, Integer> getPrimeira() { return primeira; } public void setPrimeira(Map<UUID, Integer> v) { primeira = v; }
        public Map<UUID, Integer> getSegunda() { return segunda; } public void setSegunda(Map<UUID, Integer> v) { segunda = v; }

        /** Rows left blank or at zero are simply not part of the inventory. */
        InventarioForm paraServico() {
            var f = new InventarioForm(); f.setIdUnidade(idUnidade); f.setReferencia(referencia);
            for (var c : cargas)
                c.getItens().forEach((formato, l) -> {
                    if (positivo(l.getLinhas()) || positivo(l.getQuantidade()))
                        f.getItens().add(new InventarioForm.Item(c.getEstado(), formato, c.getMes(), c.getNumero(), l.getLinhas(), l.getQuantidade() == null ? 0 : l.getQuantidade()));
                });
            primeira.forEach((formato, q) -> { if (positivo(q)) f.getItens().add(new InventarioForm.Item(InventarioInicial.Estado.ACABADO_1A, formato, null, null, null, q)); });
            segunda.forEach((formato, q) -> { if (positivo(q)) f.getItens().add(new InventarioForm.Item(InventarioInicial.Estado.ACABADO_2A, formato, null, null, null, q)); });
            return f;
        }
        private static boolean positivo(Integer v) { return v != null && v > 0; }
    }

    @GetMapping String tela(@AuthenticationPrincipal UsuarioAutenticado p, @RequestParam(required = false) UUID unidade, Model model) {
        var tela = new Tela(); tela.setIdUnidade(unidade);
        preparar(model, p, tela);
        return "inventario";
    }
    @PostMapping String registrar(@ModelAttribute("tela") Tela tela, @AuthenticationPrincipal UsuarioAutenticado p, Model model, RedirectAttributes flash) {
        try {
            var salvos = inventario.registrar(p, tela.paraServico());
            flash.addFlashAttribute("sucesso", "Inventário inicial registrado: " + salvos.size() + " item(ns). A partir de agora, só entram lançamentos depois desta data.");
            return "redirect:/admin/inventario?unidade=" + tela.getIdUnidade();
        } catch (RegraNegocioException e) { model.addAttribute("mensagemErro", e.getMessage()); }
        preparar(model, p, tela);
        return "inventario";
    }
    private void preparar(Model model, UsuarioAutenticado p, Tela tela) {
        var todas = unidades.findAllByOrderByNome();
        if (tela.getIdUnidade() == null && !todas.isEmpty()) tela.setIdUnidade(todas.getFirst().getId());
        model.addAttribute("unidades", todas); model.addAttribute("tela", tela);
        model.addAttribute("formatos", producao.formatosAtivos());
        model.addAttribute("estadosNoForno", List.of(InventarioInicial.Estado.AGUARDANDO_QUEIMA, InventarioInicial.Estado.ASSADO));
        model.addAttribute("registrado", tela.getIdUnidade() == null ? List.of() : inventario.consultar(tela.getIdUnidade(), p));
        model.addAttribute("agora", LocalDateTime.now(RegistroApoio.FUSO).withSecond(0).withNano(0));
    }
}
