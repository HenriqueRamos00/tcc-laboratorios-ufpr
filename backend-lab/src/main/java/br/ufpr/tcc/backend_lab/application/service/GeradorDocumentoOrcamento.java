package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.OrcamentoItem;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class GeradorDocumentoOrcamento {
    private static final Locale LOCALE_BRASIL = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE_BRASIL);

    public byte[] gerar(Orcamento orcamento) {
        try (var output = new ByteArrayOutputStream()) {
            var documento = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(documento, output);
            documento.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font rotulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font texto = FontFactory.getFont(FontFactory.HELVETICA, 10);
            documento.add(new Paragraph("Proposta comercial", titulo));
            documento.add(new Paragraph(" "));
            adicionarCampo(documento, "Proposta", orcamento.getCode(), rotulo, texto);
            adicionarCampo(documento, "Nome", orcamento.getName(), rotulo, texto);
            adicionarCampo(documento, "Empresa", orcamento.getEmpresa().getLegalName(), rotulo, texto);
            adicionarCampo(documento, "CNPJ", orcamento.getEmpresa().getCnpj(), rotulo, texto);
            adicionarCampo(
                    documento,
                    "Endereço",
                    orcamento.getEmpresa().getAddress()
                            + ", "
                            + orcamento.getEmpresa().getCity()
                            + "/"
                            + orcamento.getEmpresa().getState(),
                    rotulo,
                    texto);
            adicionarCampo(
                    documento,
                    "Contato",
                    valorOuFallback(
                            orcamento.getExternalContactName(),
                            orcamento.getEmpresa().getContactName()),
                    rotulo,
                    texto);
            adicionarCampo(
                    documento,
                    "E-mail",
                    valorOuFallback(
                            orcamento.getExternalContactEmail(),
                            orcamento.getEmpresa().getContactEmail()),
                    rotulo,
                    texto);
            adicionarCampo(documento, "Data de criação", FORMATO_DATA.format(orcamento.getCreatedDate()), rotulo, texto);
            adicionarCampo(documento, "Descrição", orcamento.getDescription(), rotulo, texto);

            if (orcamento.getScope() != null && !orcamento.getScope().isBlank()) {
                adicionarCampo(documento, "Escopo", orcamento.getScope(), rotulo, texto);
            }

            if (orcamento.getItems() != null && !orcamento.getItems().isEmpty()) {
                documento.add(new Paragraph("Itens da proposta", rotulo));
                PdfPTable tabela = new PdfPTable(new float[] {4, 1, 2, 2});
                tabela.setWidthPercentage(100);
                adicionarCelula(tabela, "Ensaio", rotulo);
                adicionarCelula(tabela, "Amostras", rotulo);
                adicionarCelula(tabela, "Norma", rotulo);
                adicionarCelula(tabela, "Valor unitário", rotulo);
                for (OrcamentoItem item : orcamento.getItems()) {
                    adicionarCelula(tabela, item.getTestType(), texto);
                    adicionarCelula(tabela, Integer.toString(item.getSampleCount()), texto);
                    adicionarCelula(tabela, textoOuVazio(item.getStandard()), texto);
                    adicionarCelula(tabela, moeda(item.getUnitPrice()), texto);
                }
                documento.add(tabela);
            }

            documento.add(new Paragraph(" "));
            adicionarCampo(documento, "Valor total", moeda(orcamento.getTotalPrice()), rotulo, texto);
            documento.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Não foi possível gerar o PDF do orçamento", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Não foi possível montar o PDF do orçamento", exception);
        }
    }

    private static void adicionarCampo(
            Document documento, String rotulo, String valor, Font fonteRotulo, Font fonteTexto)
            throws DocumentException {
        documento.add(new Paragraph(rotulo + ":", fonteRotulo));
        documento.add(new Paragraph(textoOuVazio(valor), fonteTexto));
    }

    private static void adicionarCelula(PdfPTable tabela, String valor, Font fonte) {
        tabela.addCell(new Phrase(textoOuVazio(valor), fonte));
    }

    private static String valorOuFallback(String valor, String fallback) {
        return valor == null ? fallback : valor;
    }

    private static String textoOuVazio(String valor) {
        return valor == null ? "" : valor;
    }

    private static String moeda(java.math.BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(LOCALE_BRASIL).format(valor);
    }
}
