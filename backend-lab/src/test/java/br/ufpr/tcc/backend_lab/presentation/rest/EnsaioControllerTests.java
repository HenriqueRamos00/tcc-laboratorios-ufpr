package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.HistoricoStatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.InstrumentoLaboratorial;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.HistoricoStatusEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.InstrumentoLaboratorialRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.usecase.CriarEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.AtualizarEnsaioUseCase;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class EnsaioControllerTests {
    private static final String PATH = "/api/tests";
    private static final String MEDICOES = "\"measurements\":["
            + "{\"code\":\"zero\",\"name\":\"Zero\",\"result\":{\"kind\":\"quantity\",\"value\":\"0\"}},"
            + "{\"code\":\"nd\",\"name\":\"Não detectado\",\"result\":{\"kind\":\"not-detected\"}},"
            + "{\"code\":\"np\",\"name\":\"Não realizado\",\"result\":{\"kind\":\"not-performed\"}},"
            + "{\"code\":\"texto\",\"name\":\"Texto\",\"result\":{\"kind\":\"text\",\"text\":\"0,01%\"}},"
            + "{\"code\":\"gas\",\"name\":\"Gás\",\"result\":{\"kind\":\"quantity\",\"value\":\"12.3\"}}]";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EmpresaRepository empresas;
    @Autowired private EquipamentoRepository equipamentos;
    @Autowired private OrcamentoRepository orcamentos;
    @Autowired private AmostraRepository amostras;
    @Autowired private InstrumentoLaboratorialRepository instrumentos;
    @MockitoSpyBean private ExecucaoEnsaioRepository ensaios;
    @MockitoSpyBean private HistoricoStatusEnsaioRepository historicoStatus;
    @Autowired private JwtService jwtService;
    @Autowired private CriarEnsaioUseCase criarEnsaio;
    @Autowired private AtualizarEnsaioUseCase atualizarEnsaio;
    @Autowired private PlatformTransactionManager transactionManager;

    private Amostra amostra;
    private InstrumentoLaboratorial instrumento;
    private InstrumentoLaboratorial instrumentoSecundario;
    private Usuario tecnico;

    @BeforeEach
    void prepararReferencias() {
        historicoStatus.deleteAll();
        ensaios.deleteAll();
        amostras.deleteAll();
        equipamentos.deleteAll();
        instrumentos.deleteAll();
        orcamentos.deleteAll();
        usuarios.deleteAll();
        empresas.deleteAll();

        Empresa empresa = new Empresa();
        empresa.setLegalName("Empresa de ensaio");
        empresa.setCnpj("11222333000181");
        empresa.setAddress("Rua de teste, 1");
        empresa.setCity("Curitiba");
        empresa.setState("PR");
        empresa.setContactName("Contato");
        empresa.setContactPhone("41999999999");
        empresa.setContactEmail("contato@example.test");
        empresa = empresas.saveAndFlush(empresa);

        var quote = new Orcamento();
        quote.setEmpresa(empresa);
        quote.setCode("TEST-HU25-001");
        quote.setProtocol("L-TEST-2026-001-X");
        quote.setName("Proposta de teste");
        quote.setStatus(StatusComercialOrcamento.IN_PROGRESS);
        quote.setStage("Recebido");
        quote.setDescription("Proposta usada no teste da execução");
        quote.setTotalPrice(new BigDecimal("100.00"));
        quote.setCreatedDate(OffsetDateTime.now());
        quote = orcamentos.saveAndFlush(quote);

        amostra = new Amostra();
        amostra.setCodigo("A-TEST-001/2026");
        amostra.setDescricao("Amostra de teste");
        amostra.setOrcamento(quote);
        amostra = amostras.saveAndFlush(amostra);

        instrumento = new InstrumentoLaboratorial();
        instrumento.setNumeroPatrimonio("INS-TEST-001");
        instrumento.setNome("Instrumento de teste");
        instrumento = instrumentos.saveAndFlush(instrumento);

        instrumentoSecundario = new InstrumentoLaboratorial();
        instrumentoSecundario.setNumeroPatrimonio("INS-TEST-002");
        instrumentoSecundario.setNome("Segundo instrumento de teste");
        instrumentoSecundario = instrumentos.saveAndFlush(instrumentoSecundario);

        tecnico = new Usuario();
        tecnico.setNome("Técnico de teste");
        tecnico.setEmail("tecnico@example.test");
        tecnico.setSenhaHash("hash-de-teste");
        tecnico.setPerfil(PerfilUsuario.TECNICO);
        tecnico.setAtivo(true);
        tecnico.setUnidade("DOLEO");
        tecnico = usuarios.saveAndFlush(tecnico);
    }

    @Test
    void permiteAvancoSaltoRetornoRejeicaoECancelamentoComHistorico() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.INCOMPLETE);
        String auth = bearer(tecnico);
        String id = ensaio.getId().toString();

        var avancado = mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "validated", null, ensaio.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("validated"))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.changedAt").isString())
                .andExpect(jsonPath("$.responsible.id").value(tecnico.getId().toString()))
                .andReturn();
        long versao = Long.parseLong(json(avancado.getResponse().getContentAsString(), "version"));

        var retorno = mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "complete", null, versao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("complete"))
                .andReturn();
        versao = Long.parseLong(json(retorno.getResponse().getContentAsString(), "version"));

        var rejeicao = mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("reject", null, "Resultado fora do critério", versao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("incomplete"))
                .andReturn();
        versao = Long.parseLong(json(rejeicao.getResponse().getContentAsString(), "version"));

        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("cancel", null, "Solicitação do cliente", versao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        mockMvc.perform(get(PATH + "/" + id + "/status-history").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].previousStatus").value("incomplete"))
                .andExpect(jsonPath("$[0].status").value("validated"))
                .andExpect(jsonPath("$[0].action").value("set-status"))
                .andExpect(jsonPath("$[1].previousStatus").value("validated"))
                .andExpect(jsonPath("$[1].status").value("complete"))
                .andExpect(jsonPath("$[2].action").value("reject"))
                .andExpect(jsonPath("$[2].justification").value("Resultado fora do critério"))
                .andExpect(jsonPath("$[3].action").value("cancel"))
                .andExpect(jsonPath("$[3].responsible.name").value("Técnico de teste"));

        assertEquals(StatusComercialOrcamento.IN_PROGRESS, ensaio.getOrcamento().getStatus());
        assertEquals(StatusEnsaio.CANCELLED, ensaios.findById(ensaio.getId()).orElseThrow().getStatus());
    }

    @Test
    void preservaMetadadosAoRetornarDeRelatorioPublicadoEBloqueiaNovaPublicacao() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.REPORT_PUBLISHED);
        ensaio.setReportReference("LAUDO-TESTE-001");
        OffsetDateTime publicadoEm = OffsetDateTime.parse("2026-10-31T12:00:00Z");
        ensaio.setPublishedAt(publicadoEm);
        ensaio = ensaios.saveAndFlush(ensaio);
        String auth = bearer(tecnico);
        String id = ensaio.getId().toString();

        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "drafting-report", null, ensaio.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("drafting-report"));

        ExecucaoEnsaio retornado = ensaios.findById(ensaio.getId()).orElseThrow();
        assertEquals("LAUDO-TESTE-001", retornado.getReportReference());
        assertEquals(publicadoEm.toInstant(), retornado.getPublishedAt().toInstant());

        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "report-published", null, retornado.getVersion())))
                .andExpect(status().isConflict());
        assertEquals(1, historicoStatus.count());
        assertEquals("LAUDO-TESTE-001", ensaios.findById(ensaio.getId()).orElseThrow().getReportReference());
    }

    @Test
    void rejeitaJustificativaAusenteStatusInvalidoEConflitoSemGravarHistorico() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.INCOMPLETE);
        String auth = bearer(tecnico);
        String id = ensaio.getId().toString();

        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("cancel", null, "  ", ensaio.getVersion())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "not-a-status", null, ensaio.getVersion())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "cancelled", null, ensaio.getVersion())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "complete", null, ensaio.getVersion() + 1)))
                .andExpect(status().isConflict());

        assertEquals(StatusEnsaio.INCOMPLETE, ensaios.findById(ensaio.getId()).orElseThrow().getStatus());
        assertEquals(0, historicoStatus.count());
    }

    @Test
    void repeticaoDoStatusAtualNaoCriaEventoEUsuarioSemPerfilTecnicoNaoPodeAlterar() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.INCOMPLETE);
        Usuario admin = new Usuario();
        admin.setNome("Administrador de teste");
        admin.setEmail("admin-status@example.test");
        admin.setSenhaHash("hash-de-teste");
        admin.setPerfil(PerfilUsuario.ADMIN);
        admin.setAtivo(true);
        admin = usuarios.saveAndFlush(admin);
        String id = ensaio.getId().toString();

        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "complete", null, ensaio.getVersion())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(PATH + "/" + id + "/status")
                        .header("Authorization", bearer(tecnico)).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "incomplete", null, ensaio.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.changedAt").doesNotExist())
                .andExpect(jsonPath("$.responsible").doesNotExist());
        assertEquals(0, historicoStatus.count());
    }

    @Test
    void conflitoDeConcorrenciaReverteStatusENaoGravaHistorico() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.INCOMPLETE);
        doThrow(new ObjectOptimisticLockingFailureException(ExecucaoEnsaio.class, ensaio.getId()))
                .when(ensaios).saveAndFlush(any(ExecucaoEnsaio.class));

        mockMvc.perform(patch(PATH + "/" + ensaio.getId() + "/status")
                        .header("Authorization", bearer(tecnico)).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "complete", null, ensaio.getVersion())))
                .andExpect(status().isConflict());

        assertEquals(StatusEnsaio.INCOMPLETE, ensaios.findById(ensaio.getId()).orElseThrow().getStatus());
        assertEquals(0, historicoStatus.count());
    }

    @Test
    void falhaAoRegistrarHistoricoReverteAtualizacaoDoStatus() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.INCOMPLETE);
        doThrow(new DataIntegrityViolationException("falha simulada ao registrar histórico"))
                .when(historicoStatus).saveAndFlush(any(HistoricoStatusEnsaio.class));

        mockMvc.perform(patch(PATH + "/" + ensaio.getId() + "/status")
                        .header("Authorization", bearer(tecnico)).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "complete", null, ensaio.getVersion())))
                .andExpect(status().isInternalServerError());

        assertEquals(StatusEnsaio.INCOMPLETE, ensaios.findById(ensaio.getId()).orElseThrow().getStatus());
        assertEquals(0, historicoStatus.count());
    }

    @Test
    void ensaioCanceladoPodeSerReabertoDiretamenteEHistoricoRegistraCancelamentoERetorno() throws Exception {
        ExecucaoEnsaio incompleto = criarEnsaio(StatusEnsaio.INCOMPLETE);
        String auth = bearer(tecnico);

        mockMvc.perform(patch(PATH + "/" + incompleto.getId() + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("reject", null, "Não é possível rejeitar", incompleto.getVersion())))
                .andExpect(status().isConflict());
        assertEquals(0, historicoStatus.count());

        ExecucaoEnsaio cancelado = criarEnsaio(StatusEnsaio.INCOMPLETE);
        var cancelamento = mockMvc.perform(patch(PATH + "/" + cancelado.getId() + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("cancel", null, "Paralisação autorizada", cancelado.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andReturn();
        long versaoCancelamento = Long.parseLong(json(cancelamento.getResponse().getContentAsString(), "version"));

        mockMvc.perform(patch(PATH + "/" + cancelado.getId() + "/status")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("set-status", "complete", null, versaoCancelamento)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("complete"))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.version").value(greaterThan((int) cancelado.getVersion())))
                .andReturn();

        mockMvc.perform(get(PATH + "/" + cancelado.getId() + "/status-history").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].previousStatus").value("incomplete"))
                .andExpect(jsonPath("$[0].status").value("cancelled"))
                .andExpect(jsonPath("$[0].action").value("cancel"))
                .andExpect(jsonPath("$[0].justification").value("Paralisação autorizada"))
                .andExpect(jsonPath("$[1].previousStatus").value("cancelled"))
                .andExpect(jsonPath("$[1].status").value("complete"))
                .andExpect(jsonPath("$[1].action").value("set-status"))
                .andExpect(jsonPath("$[1].changedAt").isString())
                .andExpect(jsonPath("$[1].responsible.name").value("Técnico de teste"));
        assertEquals(StatusEnsaio.COMPLETE, ensaios.findById(cancelado.getId()).orElseThrow().getStatus());
    }

    @Test
    void ensaioCanceladoNaoPodeSerRejeitadoNemCanceladoNovamente() throws Exception {
        ExecucaoEnsaio cancelado = criarEnsaio(StatusEnsaio.CANCELLED);

        mockMvc.perform(patch(PATH + "/" + cancelado.getId() + "/status")
                        .header("Authorization", bearer(tecnico)).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("reject", null, "Solicitação incorreta", cancelado.getVersion())))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(PATH + "/" + cancelado.getId() + "/status")
                        .header("Authorization", bearer(tecnico)).contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest("cancel", null, "Duplicação da ação", cancelado.getVersion())))
                .andExpect(status().isConflict());

        assertEquals(StatusEnsaio.CANCELLED, ensaios.findById(cancelado.getId()).orElseThrow().getStatus());
        assertEquals(0, historicoStatus.count());
    }

    @Test
    void atualizacaoDoEnsaioPreservaStatusETemHistoricoSomentePorTransicao() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.COMPLETE);
        String versao = "\"version\":" + ensaio.getVersion() + ",";

        mockMvc.perform(put(PATH + "/" + ensaio.getId()).header("Authorization", bearer(tecnico))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(versao, MEDICOES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("complete"));

        assertEquals(StatusEnsaio.COMPLETE, ensaios.findById(ensaio.getId()).orElseThrow().getStatus());
        assertEquals(0, historicoStatus.count());
    }

    @Test
    void criaConsultaAtualizaResultadosEDetectaVersaoObsoleta() throws Exception {
        String request = corpo("\"version\":null,", MEDICOES);
        String token = bearer(tecnico);
        var criado = mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.status").value("incomplete"))
                .andExpect(jsonPath("$.measurements[0].result.kind").value("quantity"))
                .andExpect(jsonPath("$.measurements[0].result.value").value("0"))
                .andExpect(jsonPath("$.measurements[1].result.kind").value("not-detected"))
                .andExpect(jsonPath("$.measurements[1].result.value").doesNotExist())
                .andExpect(jsonPath("$.measurements[2].result.kind").value("not-performed"))
                .andExpect(jsonPath("$.measurements[3].result.text").value("0,01%"))
                .andExpect(jsonPath("$.measurements.length()").value(5))
                .andReturn();

        mockMvc.perform(get(PATH).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(get(PATH).param("search", "não existe")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        String id = json(criado.getResponse().getContentAsString(), "id");
        ExecucaoEnsaio persisted = ensaios.buscarCompleto(Long.valueOf(id)).orElseThrow();
        long version = persisted.getVersion();
        String update = corpo("\"version\":" + version + ",", MEDICOES.replace("0,01%", "0,02%"));
        mockMvc.perform(put(PATH + "/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[3].result.text").value("0,02%"))
                .andExpect(jsonPath("$.version").value(greaterThan((int) version)));
        mockMvc.perform(put(PATH + "/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("test-version-conflict"));
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.length()").value(2))
                .andExpect(jsonPath("$.measurements.length()").value(5))
                .andExpect(jsonPath("$.measurements[*].code").value(
                        contains("zero", "nd", "np", "texto", "gas")));
    }

    @Test
    void consultaTecnicaRefleteEnsaioCriadoEPreservaPublicacaoAoEditar() throws Exception {
        String token = bearer(tecnico);
        Long quoteId = amostra.getOrcamento().getId();
        var criado = mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("", MEDICOES)))
                .andExpect(status().isCreated()).andReturn();
        String id = json(criado.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/technician/quotes").param("search", "TEST-HU25-001")
                        .param("status", "incomplete").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].technicalStatus").value("incomplete"));

        ExecucaoEnsaio ensaio = ensaios.findById(Long.valueOf(id)).orElseThrow();
        OffsetDateTime publicadoEm = OffsetDateTime.parse("2026-10-31T12:00:00Z");
        ensaio.setStatus(StatusEnsaio.REPORT_PUBLISHED);
        ensaio.setReportReference("LAUDO-INTEGRACAO-001");
        ensaio.setPublishedAt(publicadoEm);
        ensaio = ensaios.saveAndFlush(ensaio);
        String versao = "\"version\":" + ensaio.getVersion() + ",";
        mockMvc.perform(put(PATH + "/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(versao, MEDICOES.replace("0,01%", "0,02%"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("report-published"));

        mockMvc.perform(get("/api/technician/quotes/" + quoteId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicalStatus").value("report-published"));
        ExecucaoEnsaio atualizado = ensaios.findById(Long.valueOf(id)).orElseThrow();
        assertEquals("LAUDO-INTEGRACAO-001", atualizado.getReportReference());
        assertEquals(publicadoEm.toInstant(), atualizado.getPublishedAt().toInstant());
        assertEquals(1, ensaios.count());
    }

    @Test
    void protegeConsultasDeReferenciaParaTecnico() throws Exception {
        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail("admin@example.test");
        admin.setSenhaHash("hash-de-teste");
        admin.setPerfil(PerfilUsuario.ADMIN);
        admin.setAtivo(true);
        admin = usuarios.saveAndFlush(admin);

        mockMvc.perform(get(PATH + "/references/samples"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH + "/references/samples").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/references/quotes").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/references/samples").header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quote.companyId").isString());
        mockMvc.perform(get(PATH + "/references/quotes").param("search", "TEST-HU25")
                        .header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("TEST-HU25-001"))
                .andExpect(jsonPath("$.items[0].companyName").value("Empresa de ensaio"))
                .andExpect(jsonPath("$.items[0].totalPrice").doesNotExist())
                .andExpect(jsonPath("$.items[0].externalContactEmail").doesNotExist());
        mockMvc.perform(get(PATH + "/references/samples").param("quoteId", "999999")
                        .header("Authorization", bearer(tecnico)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/references/analyzed-equipment").param("quoteId", "x")
                        .header("Authorization", bearer(tecnico)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + "/references/analyzed-equipment")
                        .param("quoteId", amostra.getOrcamento().getId().toString())
                        .header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get(PATH + "/references/technicians").header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unit").value("DOLEO"));
        mockMvc.perform(get(PATH + "/measurement-templates/chromatography").header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements.length()").value(11));
    }

    @Test
    void pesquisaReferenciasDeOrcamentoFiltraStatusComercialAntesDaPaginacao() throws Exception {
        criarOrcamento("TEST-HU25-002-PENDENTE", StatusComercialOrcamento.PENDING_ACCEPTANCE);
        criarOrcamento("TEST-HU25-003-RECUSADO", StatusComercialOrcamento.REFUSED);
        criarOrcamento("TEST-HU25-004-CANCELADO", StatusComercialOrcamento.CANCELLED);
        criarOrcamento("TEST-HU25-005-FINALIZADO", StatusComercialOrcamento.FINISHED);

        mockMvc.perform(get(PATH + "/references/quotes")
                        .param("search", "TEST-HU25")
                        .param("page", "1")
                        .param("pageSize", "1")
                        .header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("TEST-HU25-001"));

        mockMvc.perform(get(PATH + "/references/quotes")
                        .param("search", "TEST-HU25")
                        .param("page", "2")
                        .param("pageSize", "1")
                        .header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].code").value("TEST-HU25-005-FINALIZADO"));
    }

    @Test
    void filtraAmostrasPorOrcamentoAntesDaPaginacao() throws Exception {
        Orcamento outro = new Orcamento();
        outro.setEmpresa(amostra.getOrcamento().getEmpresa());
        outro.setCode("TEST-HU25-002");
        outro.setProtocol("L-TEST-2026-002-X");
        outro.setName("Segunda proposta");
        outro.setStatus(StatusComercialOrcamento.PENDING_ACCEPTANCE);
        outro.setStage("Negociação");
        outro.setDescription("Outra proposta usada no teste");
        outro.setTotalPrice(new BigDecimal("200.00"));
        outro.setCreatedDate(OffsetDateTime.now());
        outro = orcamentos.saveAndFlush(outro);
        Amostra outraAmostra = new Amostra();
        outraAmostra.setCodigo("A-TEST-002/2026");
        outraAmostra.setDescricao("Outra amostra");
        outraAmostra.setOrcamento(outro);
        amostras.saveAndFlush(outraAmostra);

        String token = bearer(tecnico);
        mockMvc.perform(get(PATH + "/references/samples")
                        .param("quoteId", amostra.getOrcamento().getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value(amostra.getCodigo()));
        mockMvc.perform(get(PATH + "/references/samples")
                        .param("quoteId", outro.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value("A-TEST-002/2026"));
        mockMvc.perform(get(PATH + "/references/samples").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void dadosInvalidosEVersaoAusenteRetornamQuatrocentos() throws Exception {
        String token = bearer(tecnico);
        mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("\"version\":null,", MEDICOES.replace("\"value\":\"0\"", "\"value\":\"99999999999999999\""))))
                .andExpect(status().isBadRequest());
        assertEquals(0, ensaios.count());
        mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("\"version\":null,", MEDICOES)))
                .andExpect(status().isCreated());
        var created = ensaios.findAll().getFirst();
        String missingVersion = corpo("", MEDICOES);
        mockMvc.perform(put(PATH + "/" + created.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(missingVersion))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + "?page=0").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criaNovaAmostraJuntoComEnsaioEPermiteReutilizarReferencia() throws Exception {
        String token = bearer(tecnico);
        String request = corpoNova("  A-HU25-NOVA/2026  ", null, MEDICOES);
        var created = mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sample.code").value("A-HU25-NOVA/2026"))
                .andExpect(jsonPath("$.sample.description").value("Óleo de transformador"))
                .andExpect(jsonPath("$.quote.id").value(amostra.getOrcamento().getId().toString()))
                .andReturn();
        assertEquals(2, amostras.count());

        String sampleId = json(created.getResponse().getContentAsString(), "sample.id");
        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(sampleId, "\"version\":null,", MEDICOES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sample.id").value(sampleId));
        assertEquals(2, amostras.count());
        assertEquals(2, ensaios.count());
    }

    @Test
    void rejeitaAlternativasAmbiguasEConflitoDeCodigoSemGravacaoParcial() throws Exception {
        String token = bearer(tecnico);
        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(null, "\"version\":null,", MEDICOES)))
                .andExpect(status().isBadRequest());
        String ambas = corpo(amostra.getId().toString(), "\"version\":null,", MEDICOES)
                .replace("\"type\":", "\"newSample\":{\"quoteId\":\""
                        + amostra.getOrcamento().getId() + "\",\"code\":\"A-AMBIGUA\","
                        + "\"description\":\"Descrição\",\"analyzedEquipmentId\":null},\"type\":");
        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(ambas))
                .andExpect(status().isBadRequest());

        String nova = corpoNova("A-HU25-DUPLICADA/2026", null, MEDICOES);
        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nova))
                .andExpect(status().isCreated());
        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(nova))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sample-code-conflict"))
                .andExpect(jsonPath("$.message").isString());
        assertEquals(2, amostras.count());
        assertEquals(1, ensaios.count());
    }

    @Test
    void versaoObsoletaNaoCriaIdentificacaoNova() throws Exception {
        String token = bearer(tecnico);
        var created = mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("\"version\":null,", MEDICOES)))
                .andExpect(status().isCreated()).andReturn();
        String id = json(created.getResponse().getContentAsString(), "id");
        String stale = corpoNova("A-HU25-SEM-ORFA/2026", "\"version\":0,", MEDICOES);
        mockMvc.perform(put(PATH + "/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(stale))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("test-version-conflict"));
        assertEquals(1, amostras.count());
        assertEquals(1, ensaios.count());
    }

    @Test
    void pesquisaEquipamentosPelaEmpresaERecusaVinculoIncompativel() throws Exception {
        Empresa empresaDaOutraProposta = new Empresa();
        empresaDaOutraProposta.setLegalName("Outra empresa de ensaio");
        empresaDaOutraProposta.setCnpj("22333444000181");
        empresaDaOutraProposta.setAddress("Rua de teste, 2");
        empresaDaOutraProposta.setCity("Curitiba");
        empresaDaOutraProposta.setState("PR");
        empresaDaOutraProposta.setContactName("Outro contato");
        empresaDaOutraProposta.setContactPhone("41999999998");
        empresaDaOutraProposta.setContactEmail("outro@example.test");
        empresaDaOutraProposta = empresas.saveAndFlush(empresaDaOutraProposta);
        Equipamento proprio = salvarEquipamento(amostra.getOrcamento().getEmpresa(), "SERIE-HU25-1");
        Equipamento estrangeiro = salvarEquipamento(empresaDaOutraProposta, "SERIE-HU25-2");
        String token = bearer(tecnico);
        String quoteId = amostra.getOrcamento().getId().toString();

        mockMvc.perform(get(PATH + "/references/analyzed-equipment")
                        .param("quoteId", quoteId).param("search", "TR-HU25")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(proprio.getId().toString()))
                .andExpect(jsonPath("$.items[0].tag").value("TR-HU25"))
                .andExpect(jsonPath("$.items[0].companyId").doesNotExist());

        String request = corpoNova("A-HU25-EQUIPAMENTO-INVALIDO", null, MEDICOES,
                estrangeiro.getId().toString());
        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertEquals(1, amostras.count());
        assertEquals(0, ensaios.count());
    }

    @Test
    void desfazAmostraNovaSeUmaReferenciaPosteriorFalhar() throws Exception {
        String request = corpoNova("A-HU25-ROLLBACK", null, MEDICOES)
                .replace("\"responsibleId\":\"" + tecnico.getId() + "\"",
                        "\"responsibleId\":\"999999\"");
        mockMvc.perform(post(PATH).header("Authorization", bearer(tecnico))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isNotFound());
        assertEquals(1, amostras.count());
        assertEquals(0, ensaios.count());
    }

    @Test
    void recusaCriacaoEmOrcamentoInelegivelSemDeixarAmostraOrfa() throws Exception {
        amostra.getOrcamento().setStatus(StatusComercialOrcamento.CANCELLED);
        orcamentos.saveAndFlush(amostra.getOrcamento());
        String token = bearer(tecnico);

        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("\"version\":null,", MEDICOES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O orçamento não está disponível para novos ensaios"));

        mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNova("A-HU25-INELEGIVEL", null, MEDICOES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O orçamento não está disponível para novos ensaios"));

        assertEquals(1, amostras.count());
        assertEquals(0, ensaios.count());
    }

    @Test
    void recusaTrocaParaOrcamentoInelegivelComAmostraExistenteOuNova() throws Exception {
        Empresa outraEmpresa = empresas.findById(amostra.getOrcamento().getEmpresa().getId()).orElseThrow();
        Orcamento inelegivel = criarOrcamento("TEST-HU25-CANCELADO", outraEmpresa,
                StatusComercialOrcamento.CANCELLED);
        Amostra amostraInelegivel = new Amostra();
        amostraInelegivel.setCodigo("A-TEST-CANCELADA/2026");
        amostraInelegivel.setDescricao("Amostra de orçamento cancelado");
        amostraInelegivel.setOrcamento(inelegivel);
        amostraInelegivel = amostras.saveAndFlush(amostraInelegivel);

        String token = bearer(tecnico);
        var criacao = mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("\"version\":null,", MEDICOES)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(criacao.getResponse().getContentAsString(), "id");
        long versao = ensaios.findById(Long.valueOf(id)).orElseThrow().getVersion();

        mockMvc.perform(put(PATH + "/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(amostraInelegivel.getId().toString(),
                                "\"version\":" + versao + ",", MEDICOES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O orçamento não está disponível para novos ensaios"));

        String novaAmostraInelegivel = corpoNova("A-HU25-TROCA-INELEGIVEL", "\"version\":" + versao + ",", MEDICOES)
                .replace("\"quoteId\":\"" + amostra.getOrcamento().getId() + "\"",
                        "\"quoteId\":\"" + inelegivel.getId() + "\"");
        mockMvc.perform(put(PATH + "/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(novaAmostraInelegivel))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O orçamento não está disponível para novos ensaios"));

        assertEquals(2, amostras.count());
        assertEquals(1, ensaios.count());
        assertEquals(amostra.getOrcamento().getId(), ensaios.findById(Long.valueOf(id)).orElseThrow()
                .getOrcamento().getId());
    }

    @Test
    void permiteEditarEnsaioMantendoOrcamentoHistoricoAgoraInelegivel() throws Exception {
        String token = bearer(tecnico);
        var criacao = mockMvc.perform(post(PATH).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("\"version\":null,", MEDICOES)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(criacao.getResponse().getContentAsString(), "id");
        Long quoteId = amostra.getOrcamento().getId();
        amostra.getOrcamento().setStatus(StatusComercialOrcamento.CANCELLED);
        orcamentos.saveAndFlush(amostra.getOrcamento());

        mockMvc.perform(get(PATH + "/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.id").value(quoteId.toString()));
        mockMvc.perform(get(PATH + "/references/quotes")
                        .param("search", "TEST-HU25")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        long versao = ensaios.findById(Long.valueOf(id)).orElseThrow().getVersion();
        mockMvc.perform(put(PATH + "/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("\"version\":" + versao + ",", MEDICOES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.id").value(quoteId.toString()));
        assertEquals(1, amostras.count());
        assertEquals(1, ensaios.count());
    }

    @Test
    void concorrenciaEntreCancelamentoECriacaoRevalidaStatusSobBloqueio() throws Exception {
        Long quoteId = amostra.getOrcamento().getId();
        CountDownLatch cancelamentoPreparado = new CountDownLatch(1);
        CountDownLatch liberarCancelamento = new CountDownLatch(1);
        ExecutorService cancelador =
                Executors.newSingleThreadExecutor(task -> new Thread(task, "cancelamento-orcamento-teste"));
        ExecutorService criador =
                Executors.newSingleThreadExecutor(task -> new Thread(task, "criacao-ensaio-concorrente"));

        try {
            Future<?> transacaoCancelamento = cancelador.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                        Orcamento quote = orcamentos.buscarPorIdComBloqueio(quoteId).orElseThrow();
                        quote.setStatus(StatusComercialOrcamento.CANCELLED);
                        orcamentos.saveAndFlush(quote);
                        cancelamentoPreparado.countDown();
                        aguardarLiberacao(liberarCancelamento);
                    }));

            assertTrue(cancelamentoPreparado.await(5, TimeUnit.SECONDS));
            CountDownLatch criacaoIniciada = new CountDownLatch(1);
            Future<?> tentativaCriacao = criador.submit(() -> {
                criacaoIniciada.countDown();
                return criarEnsaio.executar(
                        new EnsaioRequest(
                                null,
                                new EnsaioRequest.NewSampleRequest(
                                        quoteId.toString(), "A-CONCORRENCIA", "Amostra concorrente", null),
                                "Análise de teste",
                                "chromatography",
                                "Método de teste",
                                "Norma de teste",
                                LocalDate.of(2026, 11, 7),
                                tecnico.getId().toString(),
                                List.of(),
                                null,
                                null,
                                List.of(),
                                null));
            });

            assertTrue(criacaoIniciada.await(5, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertFalse(tentativaCriacao.isDone());
            liberarCancelamento.countDown();
            transacaoCancelamento.get(5, TimeUnit.SECONDS);

            ExecutionException falha =
                    assertThrows(
                            ExecutionException.class,
                            () -> tentativaCriacao.get(5, TimeUnit.SECONDS));
            assertInstanceOf(RegraNegocioException.class, falha.getCause());
            assertEquals(
                    "O orçamento não está disponível para novos ensaios",
                    falha.getCause().getMessage());
            assertEquals(1, amostras.count());
            assertEquals(0, ensaios.count());
        } finally {
            liberarCancelamento.countDown();
            cancelador.shutdownNow();
            criador.shutdownNow();
        }
    }

    @Test
    void bloqueioReleOrcamentoJaCarregadoComStatusComercialAtual() throws Exception {
        Long quoteId = amostra.getOrcamento().getId();
        CountDownLatch orcamentoCarregado = new CountDownLatch(1);
        CountDownLatch permitirNovoBloqueio = new CountDownLatch(1);
        ExecutorService leitor = Executors.newSingleThreadExecutor();

        try {
            Future<?> tentativaCriacao = leitor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
                        Orcamento carregado = orcamentos.findById(quoteId).orElseThrow();
                        assertEquals(StatusComercialOrcamento.IN_PROGRESS, carregado.getStatus());
                        orcamentoCarregado.countDown();
                        aguardarLiberacao(permitirNovoBloqueio);
                        criarEnsaio.executar(
                                new EnsaioRequest(
                                        null,
                                        new EnsaioRequest.NewSampleRequest(
                                                quoteId.toString(), "A-CONTEXTO-OBSOLETO", "Amostra concorrente", null),
                                        "Análise de teste",
                                        "chromatography",
                                        "Método de teste",
                                        "Norma de teste",
                                        LocalDate.of(2026, 11, 7),
                                        tecnico.getId().toString(),
                                        List.of(),
                                        null,
                                        null,
                                        List.of(),
                                        null));
                    }));

            assertTrue(orcamentoCarregado.await(5, TimeUnit.SECONDS));
            new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
                Orcamento quote = orcamentos.buscarPorIdComBloqueio(quoteId).orElseThrow();
                quote.setStatus(StatusComercialOrcamento.CANCELLED);
                orcamentos.saveAndFlush(quote);
            });
            permitirNovoBloqueio.countDown();

            ExecutionException falha = assertThrows(
                    ExecutionException.class, () -> tentativaCriacao.get(5, TimeUnit.SECONDS));
            assertInstanceOf(RegraNegocioException.class, falha.getCause());
            assertEquals(
                    "O orçamento não está disponível para novos ensaios",
                    falha.getCause().getMessage());
            assertEquals(1, amostras.count());
            assertEquals(0, ensaios.count());
        } finally {
            permitirNovoBloqueio.countDown();
            leitor.shutdownNow();
        }
    }

    @Test
    void editarVinculoHistoricoNaoSobrescreveDadosComerciaisConcorrentes() throws Exception {
        ExecucaoEnsaio ensaio = criarEnsaio(StatusEnsaio.INCOMPLETE);
        Long quoteId = ensaio.getOrcamento().getId();
        CountDownLatch ensaioCarregado = new CountDownLatch(1);
        CountDownLatch permitirEdicaoTecnica = new CountDownLatch(1);
        ExecutorService editorTecnico = Executors.newSingleThreadExecutor();

        try {
            Future<?> edicaoTecnica = editorTecnico.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
                        ExecucaoEnsaio carregado = ensaios.buscarCompleto(ensaio.getId()).orElseThrow();
                        assertEquals(StatusComercialOrcamento.IN_PROGRESS,
                                carregado.getOrcamento().getStatus());
                        long versaoEnsaio = carregado.getVersion();
                        ensaioCarregado.countDown();
                        aguardarLiberacao(permitirEdicaoTecnica);

                        atualizarEnsaio.executar(
                                carregado.getId().toString(),
                                new EnsaioRequest(
                                        carregado.getAmostra().getId().toString(),
                                        null,
                                        "Análise revisada",
                                        "chromatography",
                                        "Método revisado",
                                        "Norma revisada",
                                        LocalDate.of(2026, 11, 7),
                                        tecnico.getId().toString(),
                                        List.of(instrumento.getId().toString()),
                                        null,
                                        "Rascunho revisado",
                                        List.of(),
                                        versaoEnsaio));
                    }));

            assertTrue(ensaioCarregado.await(5, TimeUnit.SECONDS));
            new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
                Orcamento quote = orcamentos.buscarPorIdComBloqueio(quoteId).orElseThrow();
                quote.setStatus(StatusComercialOrcamento.CANCELLED);
                quote.setStage("Aprovado pelo cliente");
                quote.setName("Nome comercial atualizado");
                quote.setDescription("Descrição comercial atualizada");
                orcamentos.saveAndFlush(quote);
            });
            permitirEdicaoTecnica.countDown();
            edicaoTecnica.get(5, TimeUnit.SECONDS);

            Orcamento persistido = orcamentos.findById(quoteId).orElseThrow();
            assertEquals(StatusComercialOrcamento.CANCELLED, persistido.getStatus());
            assertEquals("Aprovado pelo cliente", persistido.getStage());
            assertEquals("Nome comercial atualizado", persistido.getName());
            assertEquals("Descrição comercial atualizada", persistido.getDescription());
        } finally {
            permitirEdicaoTecnica.countDown();
            editorTecnico.shutdownNow();
        }
    }

    private ExecucaoEnsaio criarEnsaio(StatusEnsaio status) {
        ExecucaoEnsaio ensaio = new ExecucaoEnsaio();
        ensaio.setAmostra(amostra);
        ensaio.setOrcamento(amostra.getOrcamento());
        ensaio.setResponsavel(tecnico);
        ensaio.setTipo("Análise de teste");
        ensaio.setTipoAnalise("chromatography");
        ensaio.setMetodo("Método de teste");
        ensaio.setNorma("Norma de teste");
        ensaio.setDataAgendada(LocalDate.of(2026, 10, 26));
        ensaio.setStatus(status);
        return ensaios.saveAndFlush(ensaio);
    }

    private Orcamento criarOrcamento(String codigo, StatusComercialOrcamento status) {
        return criarOrcamento(codigo, amostra.getOrcamento().getEmpresa(), status);
    }

    private Orcamento criarOrcamento(
            String codigo, Empresa empresa, StatusComercialOrcamento status) {
        Orcamento orcamento = new Orcamento();
        orcamento.setEmpresa(empresa);
        orcamento.setCode(codigo);
        orcamento.setProtocol("L-TEST-2026-" + codigo);
        orcamento.setName("Proposta de teste");
        orcamento.setStatus(status);
        orcamento.setStage("Recebido");
        orcamento.setDescription("Proposta usada no teste da execução");
        orcamento.setTotalPrice(new BigDecimal("100.00"));
        orcamento.setCreatedDate(OffsetDateTime.now());
        return orcamentos.saveAndFlush(orcamento);
    }

    private void aguardarLiberacao(CountDownLatch liberacao) {
        try {
            if (!liberacao.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tempo excedido aguardando a liberação do teste concorrente");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Teste concorrente interrompido", exception);
        }
    }

    private String statusRequest(String action, String targetStatus, String justification, long version) {
        String target = targetStatus == null ? "null" : "\"" + targetStatus + "\"";
        String reason = justification == null ? "null" : "\"" + justification + "\"";
        return "{\"action\":\"" + action + "\",\"targetStatus\":" + target
                + ",\"justification\":" + reason + ",\"version\":" + version + "}";
    }

    private String corpo(String versionField, String measurements) {
        return corpo(amostra.getId().toString(), versionField, measurements);
    }

    private String corpo(String sampleId, String versionField, String measurements) {
        String sample = sampleId == null ? "null" : "\"" + sampleId + "\"";
        return "{\"sampleId\":" + sample + ",\"type\":\"Análise de teste\","
                + "\"analysisType\":\"chromatography\",\"method\":\"Método\",\"standard\":\"Norma\","
                + "\"scheduledDate\":\"2026-10-26\",\"responsibleId\":\"" + tecnico.getId() + "\","
                + "\"equipmentIds\":[\"" + instrumento.getId() + "\",\"" + instrumentoSecundario.getId()
                + "\"],\"analysisClassification\":null,"
                + "\"reportText\":\"Rascunho\", " + versionField + measurements + "}";
    }

    private String corpoNova(String codigo, String versionField, String measurements) {
        return corpoNova(codigo, versionField, measurements, null);
    }

    private String corpoNova(String codigo, String versionField, String measurements,
            String analyzedEquipmentId) {
        String version = versionField == null ? "\"version\":null," : versionField;
        String equipamento = analyzedEquipmentId == null ? "null" : "\"" + analyzedEquipmentId + "\"";
        return "{\"sampleId\":null,\"newSample\":{\"quoteId\":\""
                + amostra.getOrcamento().getId() + "\",\"code\":\"" + codigo
                + "\",\"description\":\"Óleo de transformador\",\"analyzedEquipmentId\":" + equipamento + "},"
                + "\"type\":\"Análise de teste\",\"analysisType\":\"chromatography\","
                + "\"method\":\"Método\",\"standard\":\"Norma\",\"scheduledDate\":\"2026-10-26\","
                + "\"responsibleId\":\"" + tecnico.getId() + "\",\"equipmentIds\":[\""
                + instrumento.getId() + "\",\"" + instrumentoSecundario.getId()
                + "\"],\"analysisClassification\":null,\"reportText\":\"Rascunho\","
                + version + measurements + "}";
    }

    private Equipamento salvarEquipamento(Empresa empresa, String numeroSerie) {
        Equipamento equipamento = new Equipamento();
        equipamento.setNumeroSerie(numeroSerie);
        equipamento.setTag("TR-HU25");
        equipamento.setTipo(TipoEquipamento.TRANSFORMADOR);
        equipamento.setSubestacao("SE-HU25");
        equipamento.setTensaoPrimaria(new BigDecimal("13.800"));
        equipamento.setEmpresa(empresa);
        equipamento.setNivelRisco(NivelRisco.SAUDAVEL);
        return equipamentos.saveAndFlush(equipamento);
    }

    private String bearer(Usuario usuario) { return "Bearer " + jwtService.generateToken(usuario); }

    private String json(String body, String key) throws Exception {
        var node = JsonMapper.builder().build().readTree(body);
        for (String part : key.split("\\.")) node = node.get(part);
        return node.asString();
    }
}
