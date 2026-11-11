package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.MedicaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.EtapaAcompanhamentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "demo.seed.enabled=false",
        "spring.datasource.url=${DASHBOARD_TEST_JDBC_URL:jdbc:h2:mem:technician_dashboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${DASHBOARD_TEST_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${DASHBOARD_TEST_USERNAME:sa}",
        "spring.datasource.password=${DASHBOARD_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
@Import(TechnicianDashboardControllerTests.RelogioFixo.class)
class TechnicianDashboardControllerTests {
    private static final String CAMINHO = "/api/technician/dashboard";
    private static final Instant AGORA = Instant.parse("2026-11-08T15:00:00Z");

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioDashboard() {
            return Clock.fixed(AGORA, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AmostraRepository amostras;
    @Autowired EmpresaRepository empresas;
    @Autowired ExecucaoEnsaioRepository ensaios;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired RelatorioTecnicoRepository relatorios;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    private Usuario tecnico;
    private Usuario tecnicoAlternativo;
    private Usuario administrador;
    private Usuario cliente;
    private String tokenTecnico;
    private String tokenAdministrador;
    private String tokenCliente;
    private Amostra amostra;
    private Orcamento orcamento;
    private int sequenciaEmpresa;

    @BeforeEach
    void prepararContexto() {
        tecnico = criarUsuario(PerfilUsuario.TECNICO, "Técnico principal");
        tecnicoAlternativo = criarUsuario(PerfilUsuario.TECNICO, "Técnica alternativa");
        administrador = criarUsuario(PerfilUsuario.ADMIN, "Administrador");
        cliente = criarUsuario(PerfilUsuario.CLIENTE, "Cliente");
        tokenTecnico = token(tecnico);
        tokenAdministrador = token(administrador);
        tokenCliente = token(cliente);

        Empresa empresa = criarEmpresa();
        orcamento = criarOrcamento(empresa);
        amostra = criarAmostra(orcamento);
    }

    @Test
    @DisplayName("retorna contagens e prévia vazias sem adicionar campos fora do contrato")
    void retornaPainelVazioComContratoExplicito() throws Exception {
        MvcResult resposta = mockMvc.perform(get(CAMINHO).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testsInExecution").value(0))
                .andExpect(jsonPath("$.testsAwaitingApproval").value(0))
                .andExpect(jsonPath("$.testsFinished").value(0))
                .andExpect(jsonPath("$.pendingReportsTotal").value(0))
                .andExpect(jsonPath("$.pendingReports").isArray())
                .andExpect(jsonPath("$.pendingReports.length()").value(0))
                .andReturn();

        JsonNode corpo = objectMapper.readTree(resposta.getResponse().getContentAsString());
        assertEquals(Set.of(
                "testsInExecution", "testsAwaitingApproval", "testsFinished",
                "pendingReportsTotal", "pendingReports", "reportAnalytics"), campos(corpo));
        JsonNode analytics = corpo.get("reportAnalytics");
        assertEquals("2026-11-08", analytics.get("referenceDate").asText());
        assertEquals(6, analytics.get("monthlyReports").size());
        for (JsonNode month : analytics.get("monthlyReports")) {
            assertEquals(0, month.get("validated").asInt());
            assertEquals(0, month.get("published").asInt());
        }
        assertEquals(0, analytics.get("pendingReportAge").get("upTo7Days").asInt());
        assertEquals(0, analytics.get("pendingReportAge").get("from8To15Days").asInt());
        assertEquals(0, analytics.get("pendingReportAge").get("over15Days").asInt());
    }

    @Test
    @DisplayName("autoriza somente técnicos e preserva as respostas HTTP de autenticação")
    void restringeAcessoAoPerfilTecnico() throws Exception {
        mockMvc.perform(get(CAMINHO)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CAMINHO).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CAMINHO).header("Authorization", tokenAdministrador))
                .andExpect(status().isForbidden());

        // O filtro de autenticação interna rejeita tokens CLIENTE antes da autorização da rota.
        mockMvc.perform(get(CAMINHO).header("Authorization", tokenCliente))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CAMINHO).header("Authorization", tokenTecnico))
                .andExpect(status().isOk());

        tecnico.setAtivo(false);
        usuarios.saveAndFlush(tecnico);
        mockMvc.perform(get(CAMINHO).header("Authorization", tokenTecnico))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("mapeia os status técnicos sem incluir validados ou cancelados")
    void contaExecucoesPorRegraOperacionalDeStatus() throws Exception {
        criarExecucoes(StatusEnsaio.INCOMPLETE, 2, tecnico);
        criarExecucoes(StatusEnsaio.COMPLETE, 3, tecnicoAlternativo);
        criarExecucoes(StatusEnsaio.VALIDATED, 4, tecnico);
        criarExecucoes(StatusEnsaio.FINALIZED, 5, tecnicoAlternativo);
        criarExecucoes(StatusEnsaio.DRAFTING_REPORT, 6, tecnico);
        criarExecucoes(StatusEnsaio.REPORT_PUBLISHED, 7, tecnicoAlternativo);
        criarExecucoes(StatusEnsaio.CANCELLED, 8, tecnico);

        mockMvc.perform(get(CAMINHO).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testsInExecution").value(2))
                .andExpect(jsonPath("$.testsAwaitingApproval").value(3))
                .andExpect(jsonPath("$.testsFinished").value(18))
                .andExpect(jsonPath("$.pendingReportsTotal").value(0));
    }

    @Test
    @DisplayName("conta ensaios uma vez e limita a prévia aos seis relatórios pendentes mais recentes")
    void limitaPreviaSemAlterarTotaisNemContarMedicoesOuRelatoriosComoEnsaios() throws Exception {
        ExecucaoEnsaio primeira = criarExecucao(StatusEnsaio.INCOMPLETE, tecnico);
        adicionarMedicoes(primeira, 2);
        primeira = ensaios.saveAndFlush(primeira);
        ExecucaoEnsaio segunda = criarExecucao(StatusEnsaio.COMPLETE, tecnicoAlternativo);

        List<Long> idsPendentes = new ArrayList<>();
        for (int indice = 0; indice < 8; indice++) {
            idsPendentes.add(criarRelatorio(primeira, StatusValidacaoRelatorio.PENDING_VALIDATION, false));
        }
        idsPendentes.add(criarRelatorio(segunda, StatusValidacaoRelatorio.PENDING_VALIDATION, false));
        criarRelatorio(primeira, StatusValidacaoRelatorio.VALIDATED, false);
        criarRelatorio(segunda, StatusValidacaoRelatorio.VALIDATED, true);

        Long ensaiosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM execucoes_ensaios", Long.class);
        Long relatoriosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM relatorios_tecnicos", Long.class);
        String statusAntes = jdbc.queryForObject(
                "SELECT status FROM execucoes_ensaios WHERE id = ?", String.class, primeira.getId());

        MvcResult resposta = mockMvc.perform(get(CAMINHO).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testsInExecution").value(1))
                .andExpect(jsonPath("$.testsAwaitingApproval").value(1))
                .andExpect(jsonPath("$.testsFinished").value(0))
                .andExpect(jsonPath("$.pendingReportsTotal").value(9))
                .andExpect(jsonPath("$.pendingReports.length()").value(6))
                .andExpect(jsonPath("$.pendingReports[0].companyName").value(orcamento.getEmpresa().getLegalName()))
                .andExpect(jsonPath("$.pendingReports[0].type").value(primeira.getTipo()))
                .andExpect(jsonPath("$.pendingReports[0].validationStatus").value("pending-validation"))
                .andExpect(jsonPath("$.pendingReports[0].pdf").doesNotExist())
                .andReturn();

        JsonNode corpo = objectMapper.readTree(resposta.getResponse().getContentAsString());
        JsonNode primeiroRelatorio = corpo.get("pendingReports").get(0);
        assertEquals(Set.of("id", "companyName", "type", "validationStatus"), campos(primeiroRelatorio));
        assertEquals(idsPendentes.stream().sorted((a, b) -> b.compareTo(a)).limit(6)
                .map(String::valueOf).toList(), idsDaPrevia(corpo));
        assertEquals(ensaiosAntes, jdbc.queryForObject("SELECT COUNT(*) FROM execucoes_ensaios", Long.class));
        assertEquals(relatoriosAntes, jdbc.queryForObject("SELECT COUNT(*) FROM relatorios_tecnicos", Long.class));
        assertEquals(statusAntes, jdbc.queryForObject(
                "SELECT status FROM execucoes_ensaios WHERE id = ?", String.class, primeira.getId()));
    }

    @Test
    @DisplayName("agrupa eventos documentais nos seis meses de São Paulo e exclui datas futuras")
    void analisaMesesPelasDatasDosDocumentosSemDependerDoStatusTecnico() throws Exception {
        ExecucaoEnsaio execucao = criarExecucao(StatusEnsaio.INCOMPLETE, tecnico);
        criarRelatorioComDatas(execucao, "2026-05-01T12:00:00Z",
                "2026-06-01T02:59:59Z", "2026-06-01T03:00:00Z");
        criarRelatorioComDatas(execucao, "2026-05-31T12:00:00Z",
                "2026-06-01T03:00:00Z", null);
        criarRelatorioComDatas(execucao, "2026-10-20T12:00:00Z",
                "2026-11-01T02:59:59Z", "2026-11-01T03:00:00Z");
        criarRelatorioComDatas(execucao, "2026-11-02T12:00:00Z",
                "2026-11-08T14:59:59Z", "2026-11-08T15:01:00Z");
        criarRelatorioComDatas(execucao, "2026-11-02T12:00:00Z",
                "2026-11-09T12:00:00Z", null);
        execucao.setStatus(StatusEnsaio.CANCELLED);
        ensaios.saveAndFlush(execucao);

        mockMvc.perform(get(CAMINHO).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports.length()").value(6))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[0].month").value("2026-06"))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[0].validated").value(1))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[0].published").value(1))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[1].validated").value(0))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[1].published").value(0))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[4].validated").value(1))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[4].published").value(0))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[5].month").value("2026-11"))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[5].validated").value(1))
                .andExpect(jsonPath("$.reportAnalytics.monthlyReports[5].published").value(1));
    }

    @Test
    @DisplayName("separa pendências pelos limites de 7 e 15 dias corridos e informa o documento mais antigo")
    void analisaIdadeDesdeAPreparacaoEmDiasDoCalendario() throws Exception {
        ExecucaoEnsaio execucao = criarExecucao(StatusEnsaio.INCOMPLETE, tecnico);
        for (String createdAt : List.of("2026-11-08T15:00:00Z", "2026-11-01T03:00:00Z",
                "2026-11-01T02:59:59Z", "2026-10-24T03:00:00Z",
                "2026-10-24T02:59:59Z", "2026-05-10T12:00:00Z")) {
            criarRelatorioComDatas(execucao, createdAt, null, null);
        }
        criarRelatorioComDatas(execucao, "2026-01-01T12:00:00Z", "2026-05-01T12:00:00Z", null);

        mockMvc.perform(get(CAMINHO).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReportsTotal").value(6))
                .andExpect(jsonPath("$.reportAnalytics.pendingReportAge.upTo7Days").value(2))
                .andExpect(jsonPath("$.reportAnalytics.pendingReportAge.from8To15Days").value(2))
                .andExpect(jsonPath("$.reportAnalytics.pendingReportAge.over15Days").value(2))
                .andExpect(jsonPath("$.reportAnalytics.pendingReportAge.oldestCreatedOn").value("2026-05-10"));
    }

    private void criarRelatorioComDatas(ExecucaoEnsaio execucao, String criado, String validado, String publicado) {
        Long id = criarRelatorio(execucao, validado == null
                ? StatusValidacaoRelatorio.PENDING_VALIDATION : StatusValidacaoRelatorio.VALIDATED,
                publicado != null);
        RelatorioTecnico relatorio = relatorios.findById(id).orElseThrow();
        relatorio.setCreatedAt(OffsetDateTime.parse(criado));
        relatorio.setValidatedAt(validado == null ? null : OffsetDateTime.parse(validado));
        relatorio.setPublishedAt(publicado == null ? null : OffsetDateTime.parse(publicado));
        relatorios.saveAndFlush(relatorio);
    }

    private Set<String> campos(JsonNode objeto) {
        return new HashSet<>(objeto.propertyNames());
    }

    private List<String> idsDaPrevia(JsonNode corpo) {
        List<String> ids = new ArrayList<>();
        corpo.get("pendingReports").forEach(relatorio -> ids.add(relatorio.get("id").asText()));
        return ids;
    }

    private void criarExecucoes(StatusEnsaio status, int quantidade, Usuario responsavel) {
        for (int indice = 0; indice < quantidade; indice++) {
            criarExecucao(status, responsavel);
        }
    }

    private ExecucaoEnsaio criarExecucao(StatusEnsaio status, Usuario responsavel) {
        ExecucaoEnsaio execucao = new ExecucaoEnsaio();
        execucao.setCodigo("HU21-" + UUID.randomUUID().toString().substring(0, 24));
        execucao.setAmostra(amostra);
        execucao.setOrcamento(orcamento);
        execucao.setResponsavel(responsavel);
        execucao.setTipo("Análise técnica HU21");
        execucao.setMetodo("Método de teste");
        execucao.setNorma("Norma de teste");
        execucao.setDataAgendada(LocalDate.now());
        execucao.setStatus(status);
        return ensaios.saveAndFlush(execucao);
    }

    private void adicionarMedicoes(ExecucaoEnsaio execucao, int quantidade) {
        for (int indice = 0; indice < quantidade; indice++) {
            MedicaoEnsaio medicao = new MedicaoEnsaio();
            medicao.setExecucao(execucao);
            medicao.setCodigo("MED-" + indice);
            medicao.setNome("Medição " + indice);
            medicao.setPosicao(indice);
            medicao.setTipoResultado("quantity");
            medicao.setQuantidade(BigDecimal.valueOf(indice + 1));
            execucao.getMedicoes().add(medicao);
        }
    }

    private Long criarRelatorio(
            ExecucaoEnsaio execucao, StatusValidacaoRelatorio status, boolean publicado) {
        RelatorioTecnico relatorio = new RelatorioTecnico();
        relatorio.setExecucao(execucao);
        relatorio.setValidationStatus(status);
        relatorio.setPublished(publicado);
        return relatorios.saveAndFlush(relatorio).getId();
    }

    private Usuario criarUsuario(PerfilUsuario perfil, String nome) {
        String identificador = UUID.randomUUID().toString().substring(0, 8);
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(perfil.name().toLowerCase() + "-" + identificador + "@dashboard.test");
        usuario.setSenhaHash("hash-de-teste");
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return usuarios.saveAndFlush(usuario);
    }

    private Empresa criarEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setLegalName("Empresa HU21 " + UUID.randomUUID().toString().substring(0, 8));
        empresa.setCnpj(String.format("%014d", ++sequenciaEmpresa));
        empresa.setAddress("Rua de teste, 1");
        empresa.setCity("Curitiba");
        empresa.setState("PR");
        empresa.setContactName("Contato de teste");
        empresa.setContactPhone("41999999999");
        empresa.setContactEmail("empresa@dashboard.test");
        return empresas.saveAndFlush(empresa);
    }

    private Orcamento criarOrcamento(Empresa empresa) {
        Orcamento novo = new Orcamento();
        novo.setEmpresa(empresa);
        novo.setCode("HU21-" + UUID.randomUUID());
        novo.setProtocol("L-HU21-" + UUID.randomUUID().toString().substring(0, 8));
        novo.setName("Orçamento de teste");
        novo.setStatus(StatusComercialOrcamento.IN_PROGRESS);
        novo.setStage(EtapaAcompanhamentoOrcamento.EM_EXECUCAO.texto());
        novo.setDescription("Orçamento de fixture da dashboard técnica");
        novo.setTotalPrice(BigDecimal.ZERO);
        novo.setCreatedDate(OffsetDateTime.now());
        return orcamentos.saveAndFlush(novo);
    }

    private Amostra criarAmostra(Orcamento quote) {
        Amostra nova = new Amostra();
        nova.setCodigo("HU21-" + UUID.randomUUID());
        nova.setDescricao("Amostra de fixture da dashboard técnica");
        nova.setOrcamento(quote);
        return amostras.saveAndFlush(nova);
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwtService.generateToken(usuario);
    }
}
