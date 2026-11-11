package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoRelatorio;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoRelatorioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "demo.seed.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:report_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class RelatorioControllerTests {
    private static final String CAMINHO = "/api/reports";
    private static final String AMOSTRA_FISICO_QUIMICA = "A-HU25-DEMO-001/2026";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @MockitoSpyBean ExecucaoEnsaioRepository ensaios;
    @MockitoSpyBean RelatorioTecnicoRepository relatorios;
    @MockitoSpyBean DocumentoRelatorioRepository documentos;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean MontadorSnapshotRelatorio snapshots;

    private Usuario tecnico;
    private Usuario administrador;
    private ExecucaoEnsaio ensaioReferencia;
    private String tokenTecnico;
    private String tokenAdministrador;
    private final List<Long> relatoriosTemporarios = new ArrayList<>();
    private final List<Long> execucoesTemporarias = new ArrayList<>();

    @BeforeEach
    void autenticarPerfis() {
        tecnico = usuarios.findByEmailIgnoreCase("demo.tecnico.helena@example.test").orElseThrow();
        tokenTecnico = token(tecnico);
        administrador = new Usuario();
        administrador.setNome("Administrador de relatórios");
        administrador.setEmail("admin-relatorios@example.test");
        administrador.setSenhaHash("hash de teste");
        administrador.setPerfil(PerfilUsuario.ADMIN);
        administrador = usuarios.saveAndFlush(administrador);
        tokenAdministrador = token(administrador);
        ensaioReferencia = ensaios.buscarCompleto(idExecucao(AMOSTRA_FISICO_QUIMICA)).orElseThrow();
    }

    @AfterEach
    void removerDadosDeTeste() {
        for (Long id : relatoriosTemporarios) {
            documentos.findByRelatorioId(id).ifPresent(documentos::delete);
            relatorios.deleteById(id);
        }
        documentos.flush();
        relatorios.flush();
        for (Long id : execucoesTemporarias) {
            jdbc.update("delete from historico_status_ensaios where execucao_id = ?", id);
            ensaios.deleteById(id);
        }
        ensaios.flush();
        if (administrador != null && administrador.getId() != null) {
            usuarios.deleteById(administrador.getId());
            usuarios.flush();
        }
    }

    @Test
    @DisplayName("filtra e pagina relatórios usando os nomes públicos do contrato")
    void pesquisaFiltrosEPaginacao() throws Exception {
        mockMvc.perform(get(CAMINHO)
                        .param("search", "a-hu25-demo-001")
                        .param("validationStatus", "pending-validation")
                        .param("published", "false")
                        .param("page", "1")
                        .param("pageSize", "1")
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].validationStatus").value("pending-validation"))
                .andExpect(jsonPath("$.items[0].published").value(false))
                .andExpect(jsonPath("$.items[0].pdfAvailable").value(true));

        mockMvc.perform(get(CAMINHO).param("published", "true").header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].validationStatus").value("validated"))
                .andExpect(jsonPath("$.items[0].published").value(true))
                .andExpect(jsonPath("$.items[0].pdfAvailable").value(true))
                .andExpect(jsonPath("$.totalItems").value(2));

        mockMvc.perform(get(CAMINHO).param("pageSize", "101").header("Authorization", tokenTecnico))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-report-request"));

        mockMvc.perform(get(CAMINHO).param("published", "sim").header("Authorization", tokenTecnico))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-report-request"));
    }

    @Test
    @DisplayName("consulta disponibilidade documental em lote sem carregar PDFs na listagem")
    void listaRelatoriosSemHidratarBytesDosDocumentos() throws Exception {
        prepararEValidarRelatorioNovo();
        prepararEValidarRelatorioNovo();
        clearInvocations(documentos);

        mockMvc.perform(get(CAMINHO).param("pageSize", "100").header("Authorization", tokenTecnico))
                .andExpect(status().isOk());

        verify(documentos, never()).findByRelatorioId(any());
        verify(documentos, times(1)).buscarIdsComPdfValido(anyCollection());
    }

    @Test
    @DisplayName("permite as rotas internas apenas para técnico autenticado")
    void protegeListagemDetalheValidacaoEPdf() throws Exception {
        String id = idRelatorioDaExecucao(AMOSTRA_FISICO_QUIMICA);

        mockMvc.perform(get(CAMINHO)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CAMINHO).header("Authorization", tokenAdministrador))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CAMINHO + "/{id}", id).header("Authorization", tokenAdministrador))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CAMINHO + "/{id}/pdf", id).header("Authorization", tokenAdministrador))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CAMINHO + "/{id}/validation", id)
                        .header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("retorna dados técnicos registrados, mantendo os quatro formatos de resultado")
    void detalhePreservaDadosTecnicosEFormatosDeResultado() throws Exception {
        String id = idRelatorioDaExecucao(AMOSTRA_FISICO_QUIMICA);

        mockMvc.perform(get(CAMINHO + "/{id}", id).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("pending-validation"))
                .andExpect(jsonPath("$.test.method").value("Análise físico-química"))
                .andExpect(jsonPath("$.test.standard").value("ABNT NBR 10576"))
                .andExpect(jsonPath("$.contentFingerprint").value(matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.test.measurements[0].result.kind").value("quantity"))
                .andExpect(jsonPath("$.test.measurements[1].result.kind").value("not-performed"));

        String idCromatografia = idRelatorioDaAmostraEAnalise("A-HU25-DEMO-001/2026", "chromatography");
        mockMvc.perform(get(CAMINHO + "/{id}", idCromatografia).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.test.analysisClassification").value("alerta"))
                .andExpect(jsonPath("$.test.reportText").value("Rascunho demonstrativo. Resultado textual 0,01% preservado."))
                .andExpect(jsonPath("$.test.measurements[0].result.kind").value("quantity"))
                .andExpect(jsonPath("$.test.measurements[1].result.kind").value("not-detected"))
                .andExpect(jsonPath("$.test.measurements[2].result.kind").value("not-performed"))
                .andExpect(jsonPath("$.test.measurements[3].result.kind").value("text"))
                .andExpect(jsonPath("$.test.measurements[3].result.text").value("0,01%"));
    }

    @Test
    @DisplayName("renderiza prévia PDF real sem persistir documento nem alterar a versão")
    void pdfDeRascunhoNaoPersiste() throws Exception {
        Long id = criarRelatorioTemporario(AMOSTRA_FISICO_QUIMICA);
        long documentosAntes = documentos.count();

        MvcResult primeira = mockMvc.perform(get(CAMINHO + "/{id}/pdf", id)
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(header -> assertEquals("draft", header.getResponse().getHeader("X-Report-Document-State")))
                .andReturn();
        assertTrue(new String(primeira.getResponse().getContentAsByteArray(), 0, 5,
                java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"));

        mockMvc.perform(get(CAMINHO + "/{id}/pdf", id).header("Authorization", tokenTecnico))
                .andExpect(status().isOk());
        var relatorio = relatorios.findById(id).orElseThrow();
        assertEquals(StatusValidacaoRelatorio.PENDING_VALIDATION, relatorio.getValidationStatus());
        assertEquals(0, relatorio.getVersion());
        assertFalse(relatorio.isPublished());
        assertFalse(documentos.existsById(id));
        assertEquals(documentosAntes, documentos.count());
    }

    @Test
    @DisplayName("valida com autoria real e mantém PDF e snapshot após mudança nos dados do ensaio")
    void validaEEntregaSnapshotPersistido() throws Exception {
        Long id = criarRelatorioTemporario(AMOSTRA_FISICO_QUIMICA);
        var ensaio = ensaios.buscarCompleto(idExecucao(AMOSTRA_FISICO_QUIMICA)).orElseThrow();
        String textoOriginal = ensaio.getTextoLaudo();
        String statusEnsaio = ensaio.getStatus().codigo();
        String statusOrcamento = ensaio.getOrcamento().getStatus().name();

        mockMvc.perform(post(CAMINHO + "/{id}/validation", id)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValidacao(id, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("validated"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.validatedBy.id").value(tecnico.getId().toString()))
                .andExpect(jsonPath("$.validatedBy.name").value(tecnico.getNome()))
                .andExpect(jsonPath("$.validatedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.pdfAvailable").value(true));

        MvcResult pdfAntes = mockMvc.perform(get(CAMINHO + "/{id}/pdf", id)
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("validated",
                        result.getResponse().getHeader("X-Report-Document-State")))
                .andReturn();
        byte[] bytesPersistidos = documentos.findByRelatorioId(id).orElseThrow().getContent();
        assertArrayEquals(bytesPersistidos, pdfAntes.getResponse().getContentAsByteArray());

        Long amostraId = jdbc.queryForObject("SELECT amostra_id FROM execucoes_ensaios WHERE id = ?",
                Long.class, ensaio.getId());
        String descricaoOriginal = jdbc.queryForObject("SELECT descricao FROM amostras WHERE id = ?",
                String.class, amostraId);
        try {
            jdbc.update("UPDATE execucoes_ensaios SET texto_laudo = ? WHERE id = ?",
                    "Texto posterior à validação", ensaio.getId());
            jdbc.update("UPDATE amostras SET descricao = ? WHERE id = ?",
                    "Descrição posterior à validação", amostraId);
            var detalhe = mockMvc.perform(get(CAMINHO + "/{id}", id).header("Authorization", tokenTecnico))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.test.reportText").value(textoOriginal))
                    .andExpect(jsonPath("$.test.technicalStatus").value(statusEnsaio))
                    .andExpect(jsonPath("$.sample.description").value(descricaoOriginal))
                    .andReturn();
            assertNotNull(detalhe.getResponse().getContentAsString());

            mockMvc.perform(get(CAMINHO).param("search", "amostra de demonstração")
                            .param("validationStatus", "validated")
                            .header("Authorization", tokenTecnico))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1));
            mockMvc.perform(get(CAMINHO).param("search", "Descrição posterior à validação")
                            .param("validationStatus", "validated")
                            .header("Authorization", tokenTecnico))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(0));

            MvcResult pdfDepois = mockMvc.perform(get(CAMINHO + "/{id}/pdf", id)
                            .header("Authorization", tokenTecnico))
                    .andExpect(status().isOk())
                    .andReturn();
            assertArrayEquals(bytesPersistidos, pdfDepois.getResponse().getContentAsByteArray());
            var persistido = relatorios.findById(id).orElseThrow();
            assertEquals(StatusValidacaoRelatorio.VALIDATED, persistido.getValidationStatus());
            assertEquals(tecnico.getId(), persistido.getValidatedBy().getId());
            assertFalse(persistido.isPublished());
            assertEquals(statusOrcamento,
                    ensaios.buscarCompleto(idExecucao(AMOSTRA_FISICO_QUIMICA)).orElseThrow()
                            .getOrcamento().getStatus().name());
        } finally {
            jdbc.update("UPDATE execucoes_ensaios SET texto_laudo = ? WHERE id = ?",
                    textoOriginal, ensaio.getId());
            jdbc.update("UPDATE amostras SET descricao = ? WHERE id = ?",
                    descricaoOriginal, amostraId);
        }
    }

    @Test
    @DisplayName("rejeita versão desatualizada e concorrência sem criar dois PDFs")
    void rejeitaVersaoDesatualizadaERepeticaoConcorrente() throws Exception {
        Long id = criarRelatorioTemporario(AMOSTRA_FISICO_QUIMICA);

        mockMvc.perform(post(CAMINHO + "/{id}/validation", id)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValidacao(id, 8)))
                .andExpect(status().isConflict());
        assertFalse(documentos.existsById(id));

        var iniciem = new CountDownLatch(2);
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeiro = executor.submit(() -> validarConcorrente(id, iniciem, largada));
            var segundo = executor.submit(() -> validarConcorrente(id, iniciem, largada));
            assertTrue(iniciem.await(5, TimeUnit.SECONDS));
            largada.countDown();
            List<Integer> respostas = List.of(primeiro.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS));
            assertEquals(1, respostas.stream().filter(codigo -> codigo == 200).count());
            assertEquals(1, respostas.stream().filter(codigo -> codigo == 409).count());
        }

        var persistido = relatorios.findById(id).orElseThrow();
        assertEquals(StatusValidacaoRelatorio.VALIDATED, persistido.getValidationStatus());
        assertEquals(1, persistido.getVersion());
        assertTrue(documentos.existsById(id));
    }

    @Test
    @DisplayName("aguarda edição concorrente e rejeita a revisão do conteúdo anterior")
    void rejeitaValidacaoConcorrenteComEdicaoEmAndamento() throws Exception {
        ExecucaoEnsaio ensaio = criarExecucaoTemporaria(StatusEnsaio.DRAFTING_REPORT);
        MvcResult preparada = mockMvc.perform(post("/api/tests/{id}/reports/preparation", ensaio.getId())
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isCreated()).andReturn();
        Long reportId = Long.valueOf(campoJson(preparada, "reportId"));
        relatoriosTemporarios.add(reportId);
        String request = corpoValidacao(reportId, 0);

        var validationLockRequested = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var validating = new AtomicReference<Future<Integer>>();
            transactions.execute(status -> {
                ExecucaoEnsaio editado = ensaios.buscarParaEditar(ensaio.getId()).orElseThrow();
                editado.setTextoLaudo("Alteração gravada antes da validação");
                ensaios.saveAndFlush(editado);
                validating.set(executor.submit(() -> {
                    validationLockRequested.countDown();
                    return mockMvc.perform(post(CAMINHO + "/{id}/validation", reportId)
                                    .header("Authorization", tokenTecnico)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                            .andReturn().getResponse().getStatus();
                }));
                boolean validationReachedLock;
                try {
                    validationReachedLock = validationLockRequested.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrompido aguardando o bloqueio da validação", exception);
                }
                assertTrue(validationReachedLock);
                assertThrows(
                        TimeoutException.class,
                        () -> validating.get().get(100, TimeUnit.MILLISECONDS));
                return null;
            });
            assertEquals(409, validating.get().get(15, TimeUnit.SECONDS));
        }

        assertEquals(StatusValidacaoRelatorio.PENDING_VALIDATION,
                relatorios.findById(reportId).orElseThrow().getValidationStatus());
        assertFalse(documentos.existsById(reportId));
    }

    @Test
    @DisplayName("mantém validação e documento sem gravação quando o gerador PDF falha")
    void reverteValidacaoQuandoGeradorFalha() throws Exception {
        Long id = criarRelatorioTemporario(AMOSTRA_FISICO_QUIMICA);
        var relatorio = relatorios.buscarDetalhe(id).orElseThrow();
        var atual = snapshots.atual(relatorio);
        var test = atual.test();
        var incompleto = new RelatorioDtos.Ensaio(test.id(), test.code(), test.type(), test.analysisType(),
                test.method(), test.standard(), test.scheduledDate(), test.technicalStatus(), null,
                test.analyzedEquipment(), test.instruments(), test.analysisClassification(),
                test.reportText(), test.measurements());
        var snapshotIncompleto = new RelatorioSnapshot(atual.company(), atual.quote(), atual.sample(),
                atual.type(), atual.analysisType(), incompleto);
        doReturn(snapshotIncompleto).when(snapshots).atual(any(ExecucaoEnsaio.class));

        mockMvc.perform(post(CAMINHO + "/{id}/validation", id)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValidacao(id, 0)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("report-pdf-generation-failed"));

        var persistido = relatorios.findById(id).orElseThrow();
        assertEquals(StatusValidacaoRelatorio.PENDING_VALIDATION, persistido.getValidationStatus());
        assertEquals(0, persistido.getVersion());
        assertEquals(null, persistido.getValidatedSnapshot());
        assertEquals(null, persistido.getValidatedBy());
        assertFalse(documentos.existsById(id));
    }

    @Test
    @DisplayName("rejeita relatório sem laboratório antes de gravar validação ou PDF")
    void rejeitaValidacaoSemLaboratorioSemPersistirDocumento() throws Exception {
        Long id = criarRelatorioTemporario(AMOSTRA_FISICO_QUIMICA);
        Long orcamentoId = ensaioReferencia.getOrcamento().getId();
        String laboratorioOriginal = jdbc.queryForObject(
                "SELECT laboratory_name FROM orcamentos WHERE id = ?", String.class, orcamentoId);

        try {
            jdbc.update("UPDATE orcamentos SET laboratory_name = NULL WHERE id = ?", orcamentoId);
            String request = corpoValidacao(id, 0);
            clearInvocations(documentos);

            mockMvc.perform(post(CAMINHO + "/{id}/validation", id)
                            .header("Authorization", tokenTecnico)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid-report-request"))
                    .andExpect(jsonPath("$.message")
                            .value("Informe o laboratório no orçamento antes de validar o relatório"));

            var persistido = relatorios.findById(id).orElseThrow();
            assertEquals(StatusValidacaoRelatorio.PENDING_VALIDATION, persistido.getValidationStatus());
            assertEquals(0, persistido.getVersion());
            assertEquals(null, persistido.getValidatedSnapshot());
            assertEquals(null, persistido.getValidatedBy());
            assertFalse(documentos.existsById(id));
            verify(documentos, never()).saveAndFlush(any(DocumentoRelatorio.class));
        } finally {
            jdbc.update("UPDATE orcamentos SET laboratory_name = ? WHERE id = ?", laboratorioOriginal, orcamentoId);
        }
    }

    @Test
    @DisplayName("reverte snapshot e status quando a gravação do PDF falha")
    void reverteValidacaoQuandoGravacaoPdfFalha() throws Exception {
        Long id = criarRelatorioTemporario(AMOSTRA_FISICO_QUIMICA);
        doThrow(new DataIntegrityViolationException("falha de persistência do PDF"))
                .when(documentos).saveAndFlush(any(DocumentoRelatorio.class));

        mockMvc.perform(post(CAMINHO + "/{id}/validation", id)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValidacao(id, 0)))
                .andExpect(status().isInternalServerError());

        var persistido = relatorios.findById(id).orElseThrow();
        assertEquals(StatusValidacaoRelatorio.PENDING_VALIDATION, persistido.getValidationStatus());
        assertEquals(0, persistido.getVersion());
        assertEquals(null, persistido.getValidatedSnapshot());
        assertEquals(null, persistido.getValidatedBy());
        assertFalse(documentos.existsById(id));
    }

    @Test
    @DisplayName("prepara uma única vez e reutiliza o relatório persistido")
    void preparaIdempotentemente() throws Exception {
        ExecucaoEnsaio ensaio = criarExecucaoTemporaria(StatusEnsaio.DRAFTING_REPORT);
        String testId = ensaio.getId().toString();

        mockMvc.perform(get("/api/tests/{id}/reports", testId).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.publishedDocumentAvailable").value(false));

        MvcResult criada = mockMvc.perform(post("/api/tests/{id}/reports/preparation", testId)
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("created"))
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andReturn();
        String reportId = campoJson(criada, "reportId");
        relatoriosTemporarios.add(Long.valueOf(reportId));

        mockMvc.perform(post("/api/tests/{id}/reports/preparation", testId)
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("existing"))
                .andExpect(jsonPath("$.reportId").value(reportId))
                .andExpect(jsonPath("$.reports.length()").value(1));

        assertEquals(1, relatorios.findByExecucaoIdOrderByIdAsc(ensaio.getId()).size());
        assertEquals(StatusEnsaio.DRAFTING_REPORT,
                ensaios.findById(ensaio.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("serializa preparações concorrentes sem duplicar documentos")
    void serializaPreparacoesConcorrentes() throws Exception {
        ExecucaoEnsaio ensaio = criarExecucaoTemporaria(StatusEnsaio.DRAFTING_REPORT);
        var prontas = new CountDownLatch(2);
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> prepararConcorrente(ensaio.getId(), prontas, largada));
            var segunda = executor.submit(() -> prepararConcorrente(ensaio.getId(), prontas, largada));
            assertTrue(prontas.await(5, TimeUnit.SECONDS));
            largada.countDown();
            List<Integer> respostas = List.of(primeira.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS));
            assertEquals(1, respostas.stream().filter(codigo -> codigo == 201).count());
            assertEquals(1, respostas.stream().filter(codigo -> codigo == 200).count());
        }

        List<RelatorioTecnico> criados = relatorios.findByExecucaoIdOrderByIdAsc(ensaio.getId());
        assertEquals(1, criados.size());
        relatoriosTemporarios.add(criados.getFirst().getId());
    }

    @Test
    @DisplayName("seleciona entre documentos existentes e recusa preparação em outra etapa")
    void selecionaMultiplosERecusaEstadoIncorreto() throws Exception {
        ExecucaoEnsaio multiplo = criarExecucaoTemporaria(StatusEnsaio.DRAFTING_REPORT);
        Long primeiro = criarRelatorioParaExecucao(multiplo.getId());
        Long segundo = criarRelatorioParaExecucao(multiplo.getId());

        mockMvc.perform(post("/api/tests/{id}/reports/preparation", multiplo.getId())
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("selection-required"))
                .andExpect(jsonPath("$.reportId").value(nullValue()))
                .andExpect(jsonPath("$.reports.length()").value(2))
                .andExpect(jsonPath("$.reports[0].id").value(primeiro.toString()))
                .andExpect(jsonPath("$.reports[1].id").value(segundo.toString()));

        ExecucaoEnsaio foraDaEtapa = criarExecucaoTemporaria(StatusEnsaio.COMPLETE);
        mockMvc.perform(post("/api/tests/{id}/reports/preparation", foraDaEtapa.getId())
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("report-preparation-state-invalid"));
        assertTrue(relatorios.findByExecucaoIdOrderByIdAsc(foraDaEtapa.getId()).isEmpty());
    }

    @Test
    @DisplayName("publica PDF validado uma vez e mantém arquivo e status técnico")
    void publicaPdfValidadoEConservaDados() throws Exception {
        ExecucaoEnsaio ensaio = criarExecucaoTemporaria(StatusEnsaio.DRAFTING_REPORT);
        MvcResult preparada = mockMvc.perform(post("/api/tests/{id}/reports/preparation", ensaio.getId())
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isCreated()).andReturn();
        Long reportId = Long.valueOf(campoJson(preparada, "reportId"));
        relatoriosTemporarios.add(reportId);

        mockMvc.perform(post(CAMINHO + "/{id}/validation", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content(corpoValidacao(reportId, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("validated"))
                .andExpect(jsonPath("$.published").value(false));

        MvcResult publicado = mockMvc.perform(post(CAMINHO + "/{id}/publication", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("validated"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedBy.id").value(tecnico.getId().toString()))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(2))
                .andReturn();
        byte[] pdfPersistido = documentos.findByRelatorioId(reportId).orElseThrow().getContent();
        assertTrue(DisponibilidadeRelatorios.pdfValido(pdfPersistido));
        mockMvc.perform(get(CAMINHO + "/{id}/pdf", reportId).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(result -> assertArrayEquals(pdfPersistido, result.getResponse().getContentAsByteArray()));

        mockMvc.perform(post(CAMINHO + "/{id}/publication", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("report-already-published"));
        assertEquals(2, relatorios.findById(reportId).orElseThrow().getVersion());
        assertEquals(StatusEnsaio.DRAFTING_REPORT,
                ensaios.findById(ensaio.getId()).orElseThrow().getStatus());
        assertNotNull(publicado.getResponse().getContentAsString());

        mockMvc.perform(get("/api/tests/{id}/reports", ensaio.getId()).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedDocumentAvailable").value(true));
        mockMvc.perform(patch("/api/tests/{id}/status", ensaio.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"set-status\",\"targetStatus\":\"report-published\",\"version\":"
                                + ensaios.findById(ensaio.getId()).orElseThrow().getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("report-published"));

        ExecucaoEnsaio ensaioPublicado = ensaios.findById(ensaio.getId()).orElseThrow();
        mockMvc.perform(patch("/api/tests/{id}/status", ensaio.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"set-status\",\"targetStatus\":\"drafting-report\",\"version\":"
                                + ensaioPublicado.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("drafting-report"));
        assertTrue(disponibilidadeDisponivel(ensaio.getId()));
        assertArrayEquals(pdfPersistido, mockMvc.perform(get(CAMINHO + "/{id}/pdf", reportId)
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    @DisplayName("não publica quando o PDF validado deixou de estar acessível")
    void naoPublicaSemPdfValidadoPersistido() throws Exception {
        Long reportId = criarRelatorioTemporario(AMOSTRA_FISICO_QUIMICA);
        mockMvc.perform(post(CAMINHO + "/{id}/validation", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content(corpoValidacao(reportId, 0)))
                .andExpect(status().isOk());
        documentos.deleteById(reportId);
        documentos.flush();

        mockMvc.perform(post(CAMINHO + "/{id}/publication", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("validated-document-unavailable"));
        RelatorioTecnico persistido = relatorios.findById(reportId).orElseThrow();
        assertFalse(persistido.isPublished());
        assertEquals(StatusValidacaoRelatorio.VALIDATED, persistido.getValidationStatus());
        assertEquals(1, persistido.getVersion());
    }

    @Test
    @DisplayName("reverte a publicação se a gravação dos metadados falhar")
    void revertePublicacaoQuandoGravacaoFalha() throws Exception {
        Long reportId = prepararEValidarRelatorioNovo();
        doThrow(new DataIntegrityViolationException("falha de persistência da publicação"))
                .when(relatorios).saveAndFlush(any(RelatorioTecnico.class));

        mockMvc.perform(post(CAMINHO + "/{id}/publication", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isInternalServerError());

        RelatorioTecnico persistido = relatorios.findById(reportId).orElseThrow();
        assertFalse(persistido.isPublished());
        assertEquals(null, persistido.getPublishedAt());
        assertEquals(null, persistido.getPublishedBy());
        assertEquals(1, persistido.getVersion());
        assertFalse(disponibilidadeDisponivel(persistido.getExecucao().getId()));
        assertArrayEquals(documentos.findByRelatorioId(reportId).orElseThrow().getContent(),
                mockMvc.perform(get(CAMINHO + "/{id}/pdf", reportId)
                                .header("Authorization", tokenTecnico))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    @DisplayName("serializa publicação concorrente e conserva autoria e PDF")
    void publicaConcorrentementeUmaVez() throws Exception {
        Long reportId = prepararEValidarRelatorioNovo();
        byte[] pdfAntes = documentos.findByRelatorioId(reportId).orElseThrow().getContent();
        var prontas = new CountDownLatch(2);
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> publicarConcorrente(reportId, prontas, largada));
            var segunda = executor.submit(() -> publicarConcorrente(reportId, prontas, largada));
            assertTrue(prontas.await(5, TimeUnit.SECONDS));
            largada.countDown();
            List<Integer> respostas = List.of(primeira.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS));
            assertEquals(1, respostas.stream().filter(codigo -> codigo == 200).count());
            assertEquals(1, respostas.stream().filter(codigo -> codigo == 409).count());
        }

        RelatorioTecnico publicado = relatorios.findById(reportId).orElseThrow();
        assertTrue(publicado.isPublished());
        assertEquals(tecnico.getId(), publicado.getPublishedBy().getId());
        assertEquals(2, publicado.getVersion());
        assertArrayEquals(pdfAntes, documentos.findByRelatorioId(reportId).orElseThrow().getContent());
    }

    private int prepararConcorrente(Long testId, CountDownLatch prontas, CountDownLatch largada) throws Exception {
        prontas.countDown();
        assertTrue(largada.await(5, TimeUnit.SECONDS));
        return mockMvc.perform(post("/api/tests/{id}/reports/preparation", testId)
                        .header("Authorization", tokenTecnico))
                .andReturn().getResponse().getStatus();
    }

    private int publicarConcorrente(Long reportId, CountDownLatch prontas, CountDownLatch largada)
            throws Exception {
        prontas.countDown();
        assertTrue(largada.await(5, TimeUnit.SECONDS));
        return mockMvc.perform(post(CAMINHO + "/{id}/publication", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andReturn().getResponse().getStatus();
    }

    private Long prepararEValidarRelatorioNovo() throws Exception {
        ExecucaoEnsaio ensaio = criarExecucaoTemporaria(StatusEnsaio.DRAFTING_REPORT);
        MvcResult preparada = mockMvc.perform(post("/api/tests/{id}/reports/preparation", ensaio.getId())
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isCreated()).andReturn();
        Long reportId = Long.valueOf(campoJson(preparada, "reportId"));
        relatoriosTemporarios.add(reportId);
        mockMvc.perform(post(CAMINHO + "/{id}/validation", reportId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content(corpoValidacao(reportId, 0)))
                .andExpect(status().isOk());
        return reportId;
    }

    private boolean disponibilidadeDisponivel(Long testId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/tests/{id}/reports", testId)
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("publishedDocumentAvailable").asBoolean();
    }

    private ExecucaoEnsaio criarExecucaoTemporaria(StatusEnsaio status) {
        ExecucaoEnsaio base = ensaioReferencia;
        ExecucaoEnsaio nova = new ExecucaoEnsaio();
        nova.setCodigo("ER-" + UUID.randomUUID().toString().substring(0, 24));
        nova.setAmostra(base.getAmostra());
        nova.setOrcamento(base.getOrcamento());
        nova.setResponsavel(tecnico);
        nova.setTipo(base.getTipo());
        nova.setTipoAnalise(base.getTipoAnalise());
        nova.setMetodo(base.getMetodo());
        nova.setNorma(base.getNorma());
        nova.setDataAgendada(base.getDataAgendada());
        nova.setClassificacaoAnalise(base.getClassificacaoAnalise());
        nova.setTextoLaudo(base.getTextoLaudo());
        nova.setStatus(status);
        ExecucaoEnsaio salva = ensaios.saveAndFlush(nova);
        execucoesTemporarias.add(salva.getId());
        return salva;
    }

    private Long criarRelatorioParaExecucao(Long testId) {
        RelatorioTecnico relatorio = new RelatorioTecnico();
        relatorio.setExecucao(ensaios.getReferenceById(testId));
        Long id = relatorios.saveAndFlush(relatorio).getId();
        relatoriosTemporarios.add(id);
        return id;
    }

    private String campoJson(MvcResult result, String campo) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(campo).asText();
    }

    private String corpoValidacao(Long id, long version) throws Exception {
        MvcResult detalhe = mockMvc.perform(get(CAMINHO + "/{id}", id)
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andReturn();
        String fingerprint = campoJson(detalhe, "contentFingerprint");
        return "{\"version\":" + version + ",\"contentFingerprint\":\"" + fingerprint + "\"}";
    }

    private int validarConcorrente(Long id, CountDownLatch iniciem, CountDownLatch largada) throws Exception {
        iniciem.countDown();
        assertTrue(largada.await(5, TimeUnit.SECONDS));
        return mockMvc.perform(post(CAMINHO + "/{id}/validation", id)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValidacao(id, 0)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Long criarRelatorioTemporario(String codigoEnsaio) {
        RelatorioTecnico relatorio = new RelatorioTecnico();
        relatorio.setExecucao(ensaios.getReferenceById(idExecucao(codigoEnsaio)));
        relatoriosTemporarios.add(relatorios.saveAndFlush(relatorio).getId());
        return relatoriosTemporarios.getLast();
    }

    private Long idExecucao(String codigoAmostra) {
        return jdbc.queryForObject("SELECT e.id FROM execucoes_ensaios e "
                + "JOIN amostras a ON a.id = e.amostra_id "
                + "WHERE a.codigo = ? AND e.tipo_analise = 'physical-chemical'",
                Long.class, codigoAmostra);
    }

    private String idRelatorioDaExecucao(String codigoAmostra) {
        return jdbc.queryForObject("SELECT r.id FROM relatorios_tecnicos r "
                + "JOIN execucoes_ensaios e ON e.id = r.execucao_id "
                + "JOIN amostras a ON a.id = e.amostra_id "
                + "WHERE a.codigo = ? AND e.tipo_analise = 'physical-chemical'",
                Long.class, codigoAmostra).toString();
    }

    private String idRelatorioDaAmostraEAnalise(String codigoAmostra, String tipoAnalise) {
        return jdbc.queryForObject("SELECT r.id FROM relatorios_tecnicos r "
                + "JOIN execucoes_ensaios e ON e.id = r.execucao_id "
                + "JOIN amostras a ON a.id = e.amostra_id WHERE a.codigo = ? AND e.tipo_analise = ?",
                Long.class, codigoAmostra, tipoAnalise).toString();
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwtService.generateToken(usuario);
    }
}
