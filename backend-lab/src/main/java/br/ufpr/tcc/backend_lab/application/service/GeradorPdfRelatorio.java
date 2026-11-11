package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.domain.exception.FalhaGeracaoPdfRelatorioException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GeradorPdfRelatorio {
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm XXX");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Color AZUL_PETROLEO = new Color(27, 84, 101);
    private final Font titulo;
    private final Font subtitulo;
    private final Font normal;
    private final Font pequeno;
    private final Font rotulo;

    public GeradorPdfRelatorio() {
        try {
            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.EMBEDDED);
            titulo = new Font(base, 18, Font.BOLD, AZUL_PETROLEO);
            subtitulo = new Font(base, 10, Font.BOLD, AZUL_PETROLEO);
            normal = new Font(base, 9, Font.NORMAL, Color.DARK_GRAY);
            pequeno = new Font(base, 8, Font.NORMAL, Color.DARK_GRAY);
            rotulo = new Font(base, 9, Font.BOLD, Color.DARK_GRAY);
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("Não foi possível preparar as fontes do relatório", exception);
        }
    }

    public byte[] gerarRascunho(String id, RelatorioSnapshot snapshot, OffsetDateTime geradoEm) {
        return gerar(id, snapshot, geradoEm, null, null, true);
    }

    public byte[] gerarValidado(String id, RelatorioSnapshot snapshot, OffsetDateTime validadoEm,
            RelatorioDtos.PessoaRef validador) {
        return gerar(id, snapshot, validadoEm, validador, validadoEm, false);
    }

    private byte[] gerar(String id, RelatorioSnapshot snapshot, OffsetDateTime dataDocumento,
            RelatorioDtos.PessoaRef validador, OffsetDateTime validadoEm, boolean rascunho) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 42, 48);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            document.addTitle("Relatório técnico " + id);
            document.addAuthor(snapshot.test().responsible().name());
            document.open();
            adicionarCabecalho(document, id, rascunho);
            adicionarIdentificacao(document, snapshot);
            adicionarEnsaio(document, snapshot, rascunho);
            adicionarMedicoes(document, snapshot);
            adicionarConclusao(document, snapshot);
            adicionarResponsabilidades(document, snapshot, validador, dataDocumento, validadoEm, rascunho);
            writer.flush();
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            if (exception instanceof FalhaGeracaoPdfRelatorioException falha) {
                throw falha;
            }
            throw new FalhaGeracaoPdfRelatorioException("Falha ao gerar o PDF do relatório", exception);
        }
    }

    private void adicionarCabecalho(Document document, String id, boolean rascunho) throws DocumentException {
        Paragraph title = new Paragraph("Relatório técnico", titulo);
        title.setSpacingAfter(3);
        document.add(title);
        Paragraph reference = new Paragraph("Registro " + id, subtitulo);
        reference.setSpacingAfter(8);
        document.add(reference);
        if (rascunho) {
            Paragraph marker = new Paragraph("RASCUNHO - NÃO VALIDADO", rotulo);
            marker.setSpacingAfter(12);
            document.add(marker);
        }
    }

    private void adicionarIdentificacao(Document document, RelatorioSnapshot snapshot) throws DocumentException {
        document.add(campo("Empresa", snapshot.company().name()));
        document.add(campo("Orçamento", snapshot.quote().code()));
        document.add(campo("Protocolo do laboratório", snapshot.quote().protocol()));
        document.add(campo("Laboratório", snapshot.quote().laboratoryName()));
        document.add(campo("Amostra", snapshot.sample().code() + " - " + snapshot.sample().description()));
        document.add(campo("Tipo", snapshot.type()));
        document.add(campo("Tipo de análise", tipoAnalise(snapshot.analysisType())));
    }

    private void adicionarEnsaio(Document document, RelatorioSnapshot snapshot, boolean rascunho)
            throws DocumentException {
        var test = snapshot.test();
        adicionarSecao(document, "IDENTIFICAÇÃO DO ENSAIO");
        document.add(campo("Registro do ensaio", test.code()));
        document.add(campo("Método", test.method()));
        document.add(campo("Norma", test.standard()));
        if (rascunho) {
            document.add(campo("Data agendada", dataAgendada(test.scheduledDate())));
            document.add(campo("Status do ensaio", statusEnsaio(test.technicalStatus())));
        }
        if (test.analyzedEquipment() != null) {
            document.add(campo("Equipamento analisado", test.analyzedEquipment().tag()
                    + " - série " + test.analyzedEquipment().serialNumber()));
        }
        if (!test.instruments().isEmpty()) {
            document.add(campo("Instrumentos", test.instruments().stream()
                    .map(instrument -> instrument.inventoryNumber() + " - " + instrument.name())
                    .collect(Collectors.joining("; "))));
        }
    }

    private void adicionarMedicoes(Document document, RelatorioSnapshot snapshot) throws DocumentException {
        adicionarSecao(document, "RESULTADOS REGISTRADOS");
        PdfPTable table = new PdfPTable(new float[] {3.4f, 1.1f, 2.1f, 3.4f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitRows(true);
        adicionarCelula(table, "Parâmetro", true);
        adicionarCelula(table, "Unidade", true);
        adicionarCelula(table, "Referência", true);
        adicionarCelula(table, "Resultado", true);
        if (snapshot.test().measurements().isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("Nenhuma medição registrada", normal));
            cell.setColspan(4);
            cell.setPadding(6);
            table.addCell(cell);
        } else {
            for (RelatorioDtos.Medicao measurement : snapshot.test().measurements()) {
                adicionarCelula(table, measurement.name(), false);
                adicionarCelula(table, measurement.unit(), false);
                adicionarCelula(table, measurement.referenceLimit(), false);
                adicionarCelula(table, resultado(measurement.result()), false);
            }
        }
        document.add(table);
    }

    private void adicionarConclusao(Document document, RelatorioSnapshot snapshot) throws DocumentException {
        adicionarSecao(document, "CLASSIFICAÇÃO E TEXTO TÉCNICO REGISTRADOS");
        document.add(campo("Classificação", classificacao(snapshot.test().analysisClassification())));
        document.add(campo("Texto técnico", snapshot.test().reportText()));
    }

    private void adicionarResponsabilidades(Document document, RelatorioSnapshot snapshot,
            RelatorioDtos.PessoaRef validador, OffsetDateTime dataDocumento,
            OffsetDateTime validadoEm, boolean rascunho) throws DocumentException {
        adicionarSecao(document, "RESPONSABILIDADE E DATAS");
        document.add(campo("Responsável pelo ensaio", snapshot.test().responsible().name()));
        if (rascunho) {
            document.add(campo("Prévia gerada em", DATA_HORA.format(dataDocumento)));
            document.add(new Paragraph("Documento interno para revisão. A validação não representa publicação ao cliente nem assinatura digital.", pequeno));
        } else {
            document.add(campo("Validado por", validador.name()));
            document.add(campo("Validado em", DATA_HORA.format(validadoEm)));
            document.add(new Paragraph("Validação registrada no sistema. Este arquivo não declara assinatura digital nem publicação ao cliente.", pequeno));
        }
    }

    private Paragraph campo(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Phrase(label + ": ", rotulo));
        paragraph.add(new Phrase(valorOuAusente(value), normal));
        paragraph.setSpacingAfter(3);
        return paragraph;
    }

    private void adicionarSecao(Document document, String text) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, subtitulo);
        paragraph.setSpacingBefore(3);
        paragraph.setSpacingAfter(5);
        document.add(paragraph);
    }

    private void adicionarCelula(PdfPTable table, String text, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(valorOuAusente(text), header ? rotulo : pequeno));
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        if (header) {
            cell.setBackgroundColor(new Color(232, 241, 243));
        }
        table.addCell(cell);
    }

    private String resultado(RelatorioDtos.Resultado resultado) {
        return switch (resultado.kind()) {
            case "quantity" -> quantidade(resultado.value());
            case "text" -> valorOuAusente(resultado.text());
            case "not-detected" -> "Não detectado";
            case "not-performed" -> "Não realizado";
            default -> throw new IllegalStateException("Tipo de resultado de medição desconhecido");
        };
    }

    private String tipoAnalise(String value) {
        if (value == null || value.isBlank()) return "Não informado";
        return switch (value) {
            case "physical-chemical" -> "Físico-químico";
            case "chromatography" -> "Cromatografia";
            default -> "Outro tipo";
        };
    }

    private String statusEnsaio(String value) {
        if (value == null || value.isBlank()) return "Não informado";
        return switch (value) {
            case "incomplete" -> "Em execução";
            case "complete" -> "Completo";
            case "validated" -> "Validado";
            case "finalized" -> "Finalizado";
            case "drafting-report" -> "Elaborando relatório";
            case "report-published" -> "Relatório publicado";
            case "cancelled" -> "Cancelado";
            default -> "Status não reconhecido";
        };
    }

    private String classificacao(String value) {
        if (value == null || value.isBlank()) return "Não classificada";
        return switch (value) {
            case "normal" -> "Normal";
            case "alerta" -> "Alerta";
            case "nao-conforme" -> "Não conforme";
            default -> "Classificação não reconhecida";
        };
    }

    private String dataAgendada(String value) {
        if (value == null || value.isBlank()) return "Não informado";
        try {
            return LocalDate.parse(value).format(DATA);
        } catch (DateTimeParseException exception) {
            return value;
        }
    }

    private String quantidade(String value) {
        if (value == null || value.isBlank()) return "Não informado";
        return new BigDecimal(value).toPlainString().replace('.', ',');
    }

    private String valorOuAusente(String value) {
        return value == null || value.isBlank() ? "Não informado" : value;
    }
}
