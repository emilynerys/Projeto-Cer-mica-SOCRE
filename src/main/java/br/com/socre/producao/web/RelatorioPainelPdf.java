package br.com.socre.producao.web;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

// RF27: the Indicadores screen as a PDF, with the same filters and rules and each stage's total kept apart.
final class RelatorioPainelPdf {
    private static final Color TINTA = new Color(0x30, 0x2b, 0x27), SUAVE = new Color(0x72, 0x6a, 0x63), LINHA = new Color(0xe5, 0xdf, 0xd7),
        BARRO = new Color(0xa7, 0x41, 0x23), FUNDO = new Color(0xfa, 0xf9, 0xf7), CREME = new Color(0xf4, 0xe8, 0xdc),
        VERDE = new Color(0x27, 0x60, 0x4b), VERMELHO = new Color(0x8d, 0x2e, 0x1f);
    private static final Font MARCA = fonte(10, Font.BOLD, BARRO), TITULO = fonte(19, Font.BOLD, TINTA), SECAO = fonte(12, Font.BOLD, TINTA),
        TEXTO = fonte(9, Font.NORMAL, TINTA), FORTE = fonte(9, Font.BOLD, TINTA), PEQUENO = fonte(8, Font.NORMAL, SUAVE),
        CABECALHO = fonte(8, Font.BOLD, SUAVE), VALOR = fonte(15, Font.BOLD, TINTA);
    private static final TemplateAdvice.Formatos FMT = new TemplateAdvice.Formatos();

    private RelatorioPainelPdf() {}
    private static Font fonte(float tamanho, int estilo, Color cor) { return new Font(Font.HELVETICA, tamanho, estilo, cor); }

    static byte[] gerar(PainelController.Indicadores d, String unidade, String filtros, String autor, Instant quando) {
        var saida = new ByteArrayOutputStream();
        var doc = new Document(PageSize.A4, 40, 40, 40, 48);
        PdfWriter.getInstance(doc, saida);
        doc.addTitle("Indicadores de produção · SOCRE"); doc.addCreator("SOCRE");
        var rodape = new HeaderFooter(new Phrase("SOCRE · Indicadores de produção · Página ", PEQUENO), true);
        rodape.setBorder(Rectangle.NO_BORDER); rodape.setAlignment(Element.ALIGN_RIGHT);
        doc.setFooter(rodape);
        doc.open();
        cabecalho(doc, d, unidade, filtros, autor, quando);
        resumo(doc, d);
        etapas(doc, d);
        if (d.verPatio()) patio(doc, d);
        if (d.verFornadas()) fornadas(doc, d);
        if (d.verMaromba()) maromba(doc, d);
        doc.close();
        return saida.toByteArray();
    }

    private static void cabecalho(Document doc, PainelController.Indicadores d, String unidade, String filtros, String autor, Instant quando) {
        doc.add(new Paragraph("SOCRE · Cerâmica vermelha", MARCA));
        var titulo = new Paragraph("Indicadores de produção", TITULO);
        titulo.setSpacingBefore(4); titulo.setSpacingAfter(8);
        doc.add(titulo);
        doc.add(new Paragraph("Unidade: " + unidade + "   ·   Período: " + FMT.data(d.inicio()) + " a " + FMT.data(d.fim())
            + (filtros.isEmpty() ? "" : "   ·   " + filtros), FORTE));
        doc.add(new Paragraph("Gerado em " + FMT.horario(quando) + " por " + autor + ".", PEQUENO));
    }

    /** The same six numbers as the cards on the screen; Maromba stays apart because it is not finished production. */
    private static void resumo(Document doc, PainelController.Indicadores d) {
        var t = d.etapasTotal();
        var tabela = new PdfPTable(3);
        tabela.setWidthPercentage(100); tabela.setSpacingBefore(14);
        int cartoes = 0;
        if (d.verMaromba()) { cartao(tabela, "Maromba no período", d.total(), FMT.inteiro(d.total()) + " tijolos", true); cartoes++; }
        if (d.verEnforna()) { cartao(tabela, "Enforna", t.enforna(), FMT.inteiro(t.linhasEnfornadas()) + " linhas", false); cartoes++; }
        if (d.verDesenforna()) {
            cartao(tabela, "Desenforna", t.desenfornado(), FMT.inteiro(t.linhasDesenfornadas()) + " linhas", false);
            cartao(tabela, "1ª qualidade", t.primeira(), porcento(t.percentual(t.primeira())) + " do que saiu do forno", false);
            cartao(tabela, "2ª qualidade", t.segunda(), porcento(t.percentual(t.segunda())) + " do que saiu do forno", false);
            cartao(tabela, "Cru que saiu do forno", t.cru(), porcento(t.percentual(t.cru())) + " · fora da produção acabada", false);
            cartoes += 4;
        }
        // A row of the table is only drawn once it is complete.
        for (; cartoes % 3 != 0; cartoes++) { var vazio = new PdfPCell(); vazio.setBorder(Rectangle.NO_BORDER); tabela.addCell(vazio); }
        doc.add(tabela);
        nota(doc, "Valores em milheiros. Só entram registros validados. O volume de Maromba não equivale à produção acabada.");
    }
    private static void cartao(PdfPTable tabela, String nome, long tijolos, String detalhe, boolean destaque) {
        var conteudo = new Phrase(); conteudo.add(new Chunk(nome, CABECALHO)); conteudo.add(Chunk.NEWLINE);
        conteudo.add(new Chunk(FMT.milheiros(tijolos), VALOR)); conteudo.add(Chunk.NEWLINE); conteudo.add(new Chunk(detalhe, PEQUENO));
        var celula = new PdfPCell(conteudo);
        celula.setBorderColor(LINHA); celula.setPadding(8); celula.setLeading(0, 1.3f);
        if (destaque) celula.setBackgroundColor(CREME);
        tabela.addCell(celula);
    }

    private static void etapas(Document doc, PainelController.Indicadores d) {
        var colunas = new ArrayList<String>(List.of("Formato"));
        if (d.verMaromba()) colunas.add("Maromba");
        if (d.verEnforna()) colunas.add("Enforna");
        if (d.verDesenforna()) colunas.addAll(List.of("Desenfornado", "1ª", "2ª", "Cru", "Acabado"));
        var larguras = new float[colunas.size()]; Arrays.fill(larguras, 1); larguras[0] = 2.2f;
        var tabela = tabela("Etapas por formato (milheiros)", larguras, colunas.toArray(String[]::new));
        for (var e : d.etapas()) linhaEtapa(tabela, d, e, TEXTO);
        linhaEtapa(tabela, d, d.etapasTotal(), FORTE);
        doc.add(tabela);
    }
    private static void linhaEtapa(PdfPTable tabela, PainelController.Indicadores d, PainelController.Etapas e, Font fonte) {
        tabela.addCell(celula(e.nome(), fonte, Element.ALIGN_LEFT));
        var valores = new ArrayList<Long>();
        if (d.verMaromba()) valores.add(e.maromba());
        if (d.verEnforna()) valores.add(e.enforna());
        if (d.verDesenforna()) valores.addAll(List.of(e.desenfornado(), e.primeira(), e.segunda(), e.cru(), e.acabado()));
        for (long v : valores) tabela.addCell(celula(FMT.milheiros(v), fonte, Element.ALIGN_RIGHT));
    }

    /** RN19: a movement, not a stock. */
    private static void patio(Document doc, PainelController.Indicadores d) {
        var tabela = tabela("Movimento do pátio (milheiros)", new float[]{2.2f, 1.4f, 1.8f, 1}, "Formato", "Entrou (Maromba)", "Saiu para o forno (Enforna)", "Movimento");
        for (var e : d.etapas()) linhaPatio(tabela, e, TEXTO);
        linhaPatio(tabela, d.etapasTotal(), FORTE);
        doc.add(tabela);
        nota(doc, "Não é estoque: o tijolo em secagem não é contado. O movimento mostra se a Maromba produziu mais ou menos do que o forno levou no período. É aproximado, porque as perdas na secagem não são registradas.");
    }
    private static void linhaPatio(PdfPTable tabela, PainelController.Etapas e, Font fonte) {
        tabela.addCell(celula(e.nome(), fonte, Element.ALIGN_LEFT));
        for (long v : new long[]{e.maromba(), e.enforna(), e.patio()}) tabela.addCell(celula(FMT.milheiros(v), fonte, Element.ALIGN_RIGHT));
    }

    private static void fornadas(Document doc, PainelController.Indicadores d) {
        var tabela = tabela("Últimas fornadas (tijolos)", new float[]{2.5f, 1, 1.2f, 1.2f, 1.1f, 1, 1}, "Fornada", "Ocupação", "Enfornado", "Desenfornado", "1ª qualidade", "2ª qualidade", "Cru");
        if (d.porFornada().isEmpty()) vazia(tabela, "Nenhuma fornada ainda.");
        for (var f : d.porFornada()) {
            var b = f.balanco();
            var nome = new Phrase(); nome.add(new Chunk(b.fornada().getIdentificacao(), FORTE)); nome.add(Chunk.NEWLINE);
            nome.add(new Chunk((b.fornada().isEmAndamento() ? "Em andamento" : "Fechada") + (d.unidade() == null ? " · " + f.unidade() : ""), PEQUENO));
            tabela.addCell(celula(nome, Element.ALIGN_LEFT));
            tabela.addCell(celula(porcento(f.ocupacao()), TEXTO, Element.ALIGN_RIGHT));
            tabela.addCell(comDetalhe(FMT.inteiro(b.tijolosEnfornados()), b.linhasEnfornadas() + " linhas", null, null));
            tabela.addCell(comDetalhe(FMT.inteiro(b.tijolosDesenfornados()), b.linhasDesenfornadas() + " linhas", null, null));
            tabela.addCell(comDetalhe(FMT.inteiro(b.primeira()), porcento(f.primeira()), tendencia(f.variacaoPrimeira()), cor(f.variacaoPrimeira(), true)));
            tabela.addCell(comDetalhe(FMT.inteiro(b.segunda()), porcento(f.segunda()), null, null));
            tabela.addCell(comDetalhe(FMT.inteiro(b.cru()), porcento(f.cru()), tendencia(f.variacaoCru()), cor(f.variacaoCru(), false)));
        }
        doc.add(tabela);
        nota(doc, "Por fornada entram os lançamentos ainda pendentes também, como na tela Fornadas. A ocupação compara as linhas enfornadas com as linhas que o forno comporta. O p.p. compara a porcentagem com a fornada anterior: verde é melhora (mais 1ª, menos cru). Fornada em andamento ainda pode mudar.");
    }
    private static PdfPCell comDetalhe(String valor, String detalhe, String variacao, Color corVariacao) {
        var conteudo = new Phrase(); conteudo.add(new Chunk(valor, TEXTO)); conteudo.add(Chunk.NEWLINE); conteudo.add(new Chunk(detalhe, PEQUENO));
        if (variacao != null) { conteudo.add(Chunk.NEWLINE); conteudo.add(new Chunk(variacao, fonte(8, Font.BOLD, corVariacao))); }
        return celula(conteudo, Element.ALIGN_RIGHT);
    }
    /** "+3 p.p." or "-2 p.p."; the built-in PDF font has no arrows. */
    private static String tendencia(Double variacao) {
        return variacao == null ? null : (variacao >= 0 ? "+" : "-") + Math.round(Math.abs(variacao)) + " p.p.";
    }
    private static Color cor(Double variacao, boolean subirEhBom) {
        return variacao == null ? SUAVE : (subirEhBom ? variacao >= 0 : variacao <= 0) ? VERDE : VERMELHO;
    }

    private static void maromba(Document doc, PainelController.Indicadores d) {
        var colunas = new ArrayList<String>(List.of("Data"));
        d.formatos().forEach(f -> colunas.add(f.getNome()));
        colunas.add("Total de Maromba");
        var larguras = new float[colunas.size()]; Arrays.fill(larguras, 1);
        var tabela = tabela("Maromba por dia (milheiros)", larguras, colunas.toArray(String[]::new));
        if (d.diarios().isEmpty()) vazia(tabela, "Nenhum lançamento validado de Maromba no período.");
        for (var dia : d.diarios()) {
            tabela.addCell(celula(FMT.data(dia.data()), TEXTO, Element.ALIGN_LEFT));
            d.formatos().forEach(f -> tabela.addCell(celula(FMT.milheiros(dia.porFormato().getOrDefault(f.getId(), 0L)), TEXTO, Element.ALIGN_RIGHT)));
            tabela.addCell(celula(FMT.milheiros(dia.total()), FORTE, Element.ALIGN_RIGHT));
        }
        doc.add(tabela);
        if (d.diasSemRegistro() != null && !d.diasSemRegistro().isEmpty())
            nota(doc, d.diasSemRegistro().size() + " dia(s) sem registro de Maromba na unidade selecionada. Ausência de registro não significa produção zero. Lançamentos pendentes contam como registrados, mas não entram nos volumes. Datas: "
                + d.diasSemRegistro().stream().map(FMT::data).collect(Collectors.joining(", ")) + ".");
    }

    /** The title and the column names are header rows, so they repeat on each page and never stay alone at the bottom of one. */
    private static PdfPTable tabela(String titulo, float[] larguras, String... colunas) {
        var tabela = new PdfPTable(larguras);
        tabela.setWidthPercentage(100); tabela.setSpacingBefore(16);
        var cabeca = new PdfPCell(new Phrase(titulo, SECAO));
        cabeca.setColspan(colunas.length); cabeca.setBorder(Rectangle.NO_BORDER); cabeca.setPaddingBottom(7); cabeca.setPaddingLeft(0);
        tabela.addCell(cabeca);
        for (int i = 0; i < colunas.length; i++) {
            var c = celula(colunas[i], CABECALHO, i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            c.setBackgroundColor(FUNDO);
            tabela.addCell(c);
        }
        tabela.setHeaderRows(2);
        return tabela;
    }
    private static PdfPCell celula(String texto, Font fonte, int alinhamento) { return celula(new Phrase(texto, fonte), alinhamento); }
    private static PdfPCell celula(Phrase conteudo, int alinhamento) {
        var c = new PdfPCell(conteudo);
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(LINHA); c.setPadding(5); c.setPaddingBottom(6); c.setHorizontalAlignment(alinhamento); c.setLeading(0, 1.25f);
        return c;
    }
    private static void vazia(PdfPTable tabela, String texto) {
        var c = celula(texto, PEQUENO, Element.ALIGN_LEFT);
        c.setColspan(tabela.getNumberOfColumns());
        tabela.addCell(c);
    }
    private static void nota(Document doc, String texto) {
        var p = new Paragraph(texto, PEQUENO);
        p.setSpacingBefore(6);
        doc.add(p);
    }
    private static String porcento(Number valor) {
        return valor == null ? "—" : NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")).format(valor.doubleValue()) + "%";
    }
}
