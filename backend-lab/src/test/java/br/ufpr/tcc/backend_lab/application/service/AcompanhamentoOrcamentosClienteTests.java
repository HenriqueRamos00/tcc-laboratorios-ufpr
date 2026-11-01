package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.EtapaAcompanhamentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository.Andamento;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AcompanhamentoOrcamentosClienteTests {
    private final ExecucaoEnsaioRepository ensaios = mock(ExecucaoEnsaioRepository.class);
    private final AcompanhamentoOrcamentosCliente acompanhamento =
            new AcompanhamentoOrcamentosCliente(ensaios);

    @Test
    void listaVaziaNaoConsultaEnsaios() {
        assertEquals(0, acompanhamento.resolver(List.of()).size());
        verifyNoInteractions(ensaios);
    }

    @Test
    void retornaCadaEtapaRealSemInferirProgressoPeloStatusComercial() {
        for (var status : List.of(StatusComercialOrcamento.PENDING_ACCEPTANCE,
                StatusComercialOrcamento.IN_PROGRESS, StatusComercialOrcamento.FINISHED)) {
            for (var etapa : EtapaAcompanhamentoOrcamento.values()) {
                var orcamento = orcamento(status);
                orcamento.setStage(etapa.texto());
                when(ensaios.consultarAndamentos(List.of(1L))).thenReturn(List.of());
                assertEquals(etapa, acompanhamento.resolver(List.of(orcamento)).get(1L));
            }
        }
    }

    @Test
    void mapeiaEtapasTecnicasSemModificarStatusComercialOuEtapaPersistida() {
        var orcamento = orcamento(StatusComercialOrcamento.PENDING_ACCEPTANCE);
        var esperadas = Map.of(
                StatusEnsaio.INCOMPLETE, EtapaAcompanhamentoOrcamento.EM_EXECUCAO,
                StatusEnsaio.COMPLETE, EtapaAcompanhamentoOrcamento.COMPLETO,
                StatusEnsaio.VALIDATED, EtapaAcompanhamentoOrcamento.VALIDADO,
                StatusEnsaio.FINALIZED, EtapaAcompanhamentoOrcamento.FINALIZADO,
                StatusEnsaio.DRAFTING_REPORT, EtapaAcompanhamentoOrcamento.ELABORANDO_RELATORIO,
                StatusEnsaio.REPORT_PUBLISHED, EtapaAcompanhamentoOrcamento.RELATORIO_PUBLICADO,
                StatusEnsaio.CANCELLED, EtapaAcompanhamentoOrcamento.CANCELADO);
        for (var caso : esperadas.entrySet()) {
            var status = caso.getKey();
            when(ensaios.consultarAndamentos(List.of(1L)))
                    .thenReturn(List.of(andamento(1L, status, "LAUDO-1", OffsetDateTime.now())));
            assertEquals(caso.getValue(), acompanhamento.resolver(List.of(orcamento)).get(1L));
            assertEquals(StatusComercialOrcamento.PENDING_ACCEPTANCE, orcamento.getStatus());
            assertEquals("Negociação", orcamento.getStage());
        }
    }

    @Test
    void escolheMenorAvancoEIgnoraCanceladosEmUmaConsultaParaTodasAsEmpresasPermitidas() {
        var primeiro = orcamento(StatusComercialOrcamento.IN_PROGRESS);
        var segundo = orcamento(StatusComercialOrcamento.FINISHED);
        segundo.setId(2L);
        when(ensaios.consultarAndamentos(List.of(1L, 2L))).thenReturn(List.of(
                andamento(1L, StatusEnsaio.FINALIZED, null, null),
                andamento(1L, StatusEnsaio.COMPLETE, null, null),
                andamento(1L, StatusEnsaio.CANCELLED, null, null),
                andamento(2L, StatusEnsaio.VALIDATED, null, null)));
        var etapas = acompanhamento.resolver(List.of(primeiro, segundo));
        assertEquals(EtapaAcompanhamentoOrcamento.COMPLETO, etapas.get(1L));
        assertEquals(EtapaAcompanhamentoOrcamento.VALIDADO, etapas.get(2L));
    }

    @Test
    void encerramentoComercialPrevaleceSobreExecucoes() {
        when(ensaios.consultarAndamentos(List.of(1L))).thenReturn(List.of(
                andamento(1L, StatusEnsaio.INCOMPLETE, null, null)));
        assertEquals(EtapaAcompanhamentoOrcamento.RECUSADO, acompanhamento.resolver(
                List.of(orcamento(StatusComercialOrcamento.REFUSED))).get(1L));
        assertEquals(EtapaAcompanhamentoOrcamento.CANCELADO, acompanhamento.resolver(
                List.of(orcamento(StatusComercialOrcamento.CANCELLED))).get(1L));
    }

    @Test
    void publicacaoSemReferenciaOuDataNaoProduzEtapaFicticia() {
        var orcamento = orcamento(StatusComercialOrcamento.FINISHED);
        for (var execucao : List.of(
                andamento(1L, StatusEnsaio.REPORT_PUBLISHED, null, OffsetDateTime.now()),
                andamento(1L, StatusEnsaio.REPORT_PUBLISHED, " ", OffsetDateTime.now()),
                andamento(1L, StatusEnsaio.REPORT_PUBLISHED, "LAUDO-1", null))) {
            when(ensaios.consultarAndamentos(List.of(1L))).thenReturn(List.of(execucao));
            assertThrows(IllegalStateException.class, () -> acompanhamento.resolver(List.of(orcamento)));
        }
    }

    @Test
    void aliasesConhecidosSaoNormalizadosEAusenciaOuTextoDesconhecidoSaoRejeitados() {
        assertEquals(EtapaAcompanhamentoOrcamento.RECEBIDO,
                EtapaAcompanhamentoOrcamento.deTexto("  Fechado ganho "));
        assertEquals(EtapaAcompanhamentoOrcamento.RECUSADO,
                EtapaAcompanhamentoOrcamento.deTexto("Recusado"));
        assertEquals(EtapaAcompanhamentoOrcamento.ANALISE_DA_AREA,
                EtapaAcompanhamentoOrcamento.deTexto("Em revisão"));
        assertThrows(IllegalArgumentException.class, () -> EtapaAcompanhamentoOrcamento.deTexto(null));
        assertThrows(IllegalArgumentException.class, () -> EtapaAcompanhamentoOrcamento.deTexto(" "));
        assertThrows(IllegalArgumentException.class, () -> EtapaAcompanhamentoOrcamento.deTexto("Etapa futura"));
    }

    @Test
    void etapasDaCargaDemonstrativaPertencemAoContrato() throws Exception {
        String sql = new ClassPathResource("database/demo-seed.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var registros = Pattern.compile(
                "'(?:PendingAcceptance|InProgress|Finished|Refused|Cancelled)',\\s*'([^']+)'")
                .matcher(sql);
        int quantidade = 0;
        while (registros.find()) {
            EtapaAcompanhamentoOrcamento.deTexto(registros.group(1));
            quantidade++;
        }
        assertTrue(quantidade > 0);
    }

    private Orcamento orcamento(StatusComercialOrcamento status) {
        var orcamento = new Orcamento();
        orcamento.setId(1L);
        orcamento.setStatus(status);
        orcamento.setStage("Negociação");
        return orcamento;
    }

    private Andamento andamento(Long id, StatusEnsaio status, String referencia, OffsetDateTime data) {
        return new Andamento() {
            @Override
            public Long getOrcamentoId() { return id; }
            @Override
            public StatusEnsaio getStatus() { return status; }
            @Override
            public String getReportReference() { return referencia; }
            @Override
            public OffsetDateTime getPublishedAt() { return data; }
        };
    }
}
