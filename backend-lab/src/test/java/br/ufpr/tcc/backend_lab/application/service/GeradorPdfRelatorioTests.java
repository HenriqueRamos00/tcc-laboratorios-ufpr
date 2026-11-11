package br.ufpr.tcc.backend_lab.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeradorPdfRelatorioTests {
    private final GeradorPdfRelatorio gerador = new GeradorPdfRelatorio();

    @Test
    @DisplayName("apresenta rótulos em português e valores localizados no rascunho")
    void apresentaDadosLegiveisNoRascunho() throws Exception {
        byte[] pdf = gerador.gerarRascunho("3", snapshot("incomplete"), OffsetDateTime.now());

        String texto = extrairTexto(pdf);

        assertTrue(texto.contains("Tipo de análise: Físico-químico"));
        assertTrue(texto.contains("Protocolo do laboratório: L-DEMO-2026-001-X"));
        assertTrue(texto.contains("Laboratório: Laboratório de Ensaios"));
        assertTrue(texto.contains("Status do ensaio: Em execução"));
        assertTrue(texto.contains("Data agendada: 24/10/2026"));
        assertTrue(texto.contains("Classificação: Não conforme"));
        assertTrue(texto.contains("0,87000000"));
        assertFalse(texto.contains("physical-chemical"));
        assertFalse(texto.contains("incomplete"));
        assertFalse(texto.contains("nao-conforme"));
    }

    @Test
    @DisplayName("omite status e data agendada do ensaio no PDF validado")
    void omiteStatusTecnicoNoPdfValidado() throws Exception {
        byte[] pdf = gerador.gerarValidado("3", snapshot("incomplete"), OffsetDateTime.now(),
                new RelatorioDtos.PessoaRef("2", "Técnica validadora", "Laboratório"));

        String texto = extrairTexto(pdf);

        assertFalse(texto.contains("Status do ensaio"));
        assertFalse(texto.contains("Em execução"));
        assertFalse(texto.contains("Data agendada"));
        assertFalse(texto.contains("physical-chemical"));
        assertFalse(texto.contains("nao-conforme"));
    }

    private static RelatorioSnapshot snapshot(String status) {
        String tipoAnalise = "physical-chemical";
        var medicao = new RelatorioDtos.Medicao("water", "Teor de água", "ppm", "ASTM D1533", "50 ppm",
                new RelatorioDtos.Resultado("quantity", "0.87000000", null));
        var ensaio = new RelatorioDtos.Ensaio("1", "E-3/2026", "Análise físico-química de óleo", tipoAnalise,
                "Análise físico-química", "ABNT NBR 10576", "2026-10-24", status,
                new RelatorioDtos.PessoaRef("1", "Técnica responsável", "Laboratório"), null,
                List.of(), "nao-conforme", "Conclusão técnica registrada", List.of(medicao));
        return new RelatorioSnapshot(
                new RelatorioDtos.EmpresaRef("1", "Empresa de demonstração"),
                new RelatorioDtos.OrcamentoRef("1", "ORC-2026-001", "L-DEMO-2026-001-X", "Laboratório de Ensaios"),
                new RelatorioDtos.AmostraRef("1", "AM-2026-001", "Óleo isolante"),
                ensaio.type(), tipoAnalise, ensaio);
    }

    private static String extrairTexto(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }
}
