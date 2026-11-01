package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.application.dto.request.CreateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.CreateEmpresaRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.UpdateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeClienteStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeEmpresaStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateEmpresaUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.LoginClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.LoginUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateClienteUseCase;
import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.EtapaAcompanhamentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FluxoClienteTests {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired ClienteRepository clientes;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired AmostraRepository amostras;
    @Autowired ExecucaoEnsaioRepository ensaios;
    @Autowired DocumentoOrcamentoRepository documentos;
    @Autowired CreateEmpresaUseCase criarEmpresa;
    @Autowired CreateClienteUseCase criarCliente;
    @Autowired UpdateClienteUseCase editarCliente;
    @Autowired ChangeClienteStatusUseCase statusCliente;
    @Autowired ChangeEmpresaStatusUseCase statusEmpresa;
    @Autowired LoginClienteUseCase loginCliente;
    @Autowired LoginUseCase loginInterno;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    Empresa empresaA, empresaB, empresaSemVinculo;
    Cliente clienteA;
    Orcamento orcamentoA, orcamentoB, orcamentoSemVinculo;
    Usuario administrador, tecnico;
    String tokenClienteA;

    @BeforeEach
    void prepararDados() {
        empresaA = empresa("Empresa A", "00000001000136");
        empresaB = empresa("Empresa B", "00000002000180");
        empresaSemVinculo = empresa("Empresa C", "00000003000125");
        clienteA = cliente("cliente-a@example.test", empresaA);
        orcamentoA = criarOrcamento(empresaA, "QA");
        orcamentoB = criarOrcamento(empresaB, "QB");
        orcamentoSemVinculo = criarOrcamento(empresaSemVinculo, "QC");
        LoginRequest credenciais = new LoginRequest(clienteA.getUsuario().getEmail(), " senha ");
        tokenClienteA = "Bearer " + loginCliente.execute(credenciais).accessToken();
        administrador = usuario(PerfilUsuario.ADMIN, "admin-fluxo@example.test");
        tecnico = usuario(PerfilUsuario.TECNICO, "tecnico-fluxo@example.test");
    }

    @Test
    void listaSomenteOrcamentosDasEmpresasDoCliente() throws Exception {
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("QA"))
                .andExpect(jsonPath("$[0].stage").value("Negociação"))
                .andExpect(jsonPath("$[0].status").value("PendingAcceptance"))
                .andExpect(jsonPath("$[0].hasDocument").doesNotExist())
                .andExpect(jsonPath("$[0].opportunityId").doesNotExist());
    }

    @Test
    void permiteConsultarOrcamentoEPdfDaEmpresaVinculada() throws Exception {
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("Negociação"))
                .andExpect(jsonPath("$.status").value("PendingAcceptance"))
                .andExpect(jsonPath("$.hasDocument").doesNotExist())
                .andExpect(jsonPath("$.opportunityId").doesNotExist());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes("%PDF-1.4 demo".getBytes()));
    }

    @Test
    void geraPdfLegivelComDadosReaisELeituraNaoAlteraOrcamento() throws Exception {
        documentos.deleteById(orcamentoA.getId());
        documentos.flush();
        String descricaoLonga =
                ("Análise físico-química da amostra, medição e avaliação do material. ").repeat(25)
                        + "MARCADOR FINAL DA DESCRIÇÃO";
        orcamentoA.setName("Análise de óleo isolante - proposta especial");
        orcamentoA.setDescription(descricaoLonga);
        orcamentoA.setScope("Caracterização e ensaios laboratoriais com conclusão técnica.");
        orcamentoA.setTotalPrice(new BigDecimal("12345.67"));
        orcamentos.saveAndFlush(orcamentoA);
        var dataCriacao = orcamentoA.getCreatedDate();
        var statusComercial = orcamentoA.getStatus();
        var etapaPersistida = orcamentoA.getStage();

        var resposta = mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        byte[] bytes = resposta.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 500);
        assertTrue(new String(bytes, 0, 8).startsWith("%PDF-"));
        try (var reader = new PdfReader(bytes)) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(texto.contains("Proposta comercial"));
            assertTrue(texto.contains("Análise de óleo isolante"));
            assertTrue(texto.contains("Análise físico-química"));
            assertTrue(texto.contains("MARCADOR FINAL DA DESCRIÇÃO"));
            assertTrue(texto.contains("12.345,67"));
        }

        var persistido = orcamentos.findById(orcamentoA.getId()).orElseThrow();
        assertEquals(dataCriacao, persistido.getCreatedDate());
        assertEquals(statusComercial, persistido.getStatus());
        assertEquals(etapaPersistida, persistido.getStage());
        assertEquals(descricaoLonga, persistido.getDescription());
        assertFalse(documentos.existsById(orcamentoA.getId()));
    }

    @Test
    void traduzStatusComercialSemAlterarStatusPersistido() throws Exception {
        var casos = List.of(
                new StatusEsperado(StatusComercialOrcamento.PENDING_ACCEPTANCE, "Negociação"),
                new StatusEsperado(
                        StatusComercialOrcamento.REFUSED, "Fechado recusado Lactec"),
                new StatusEsperado(StatusComercialOrcamento.CANCELLED, "Cancelado"));

        for (var caso : casos) {
            orcamentoA.setStatus(caso.status());
            orcamentoA.setStage("Em negociação");
            orcamentos.saveAndFlush(orcamentoA);

            mockMvc.perform(
                            get("/api/client/quotes/{id}", orcamentoA.getId())
                                    .header("Authorization", tokenClienteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(caso.status().codigo()))
                    .andExpect(jsonPath("$.stage").value(caso.stage()));
            var persistido = orcamentos.findById(orcamentoA.getId()).orElseThrow();
            assertEquals(caso.status(), persistido.getStatus());
            assertEquals("Em negociação", persistido.getStage());
        }
    }

    @Test
    void rejeitaGravacaoDeEtapaDesconhecida() {
        orcamentoA.setStage("Etapa desconhecida");
        RuntimeException falha = assertThrows(RuntimeException.class,
                () -> orcamentos.saveAndFlush(orcamentoA));
        Throwable causa = falha;
        while (causa.getCause() != null) causa = causa.getCause();
        assertTrue(causa instanceof IllegalArgumentException);
        assertEquals("Etapa de acompanhamento desconhecida", causa.getMessage());
    }

    @Test
    void preservaEtapasReconhecidasMesmoQuandoStatusComercialMuda() throws Exception {
        var etapas = List.of(
                new EtapaEsperada("Aprovado pelo Cliente", "Aprovado pelo cliente"),
                new EtapaEsperada("Aguardando entrega da amostra", "Aguardando entrega da amostra"),
                new EtapaEsperada("Recebido", "Recebido"),
                new EtapaEsperada("Fechado ganho", "Recebido"),
                new EtapaEsperada("Protocolado", "Protocolado"),
                new EtapaEsperada("Em execução", "Em execução"),
                new EtapaEsperada("Completo", "Completo"),
                new EtapaEsperada("Validado", "Validado"),
                new EtapaEsperada("Finalizado", "Finalizado"),
                new EtapaEsperada("Elaborando relatório", "Elaborando relatório"),
                new EtapaEsperada("Relatório publicado", "Relatório publicado"));

        var statusEmAndamentoOuEncerrado =
                List.of(StatusComercialOrcamento.IN_PROGRESS, StatusComercialOrcamento.FINISHED);
        for (var statusComercial : statusEmAndamentoOuEncerrado) {
            for (var etapa : etapas) {
                orcamentoA.setStatus(statusComercial);
                orcamentoA.setStage(etapa.persistida());
                orcamentos.saveAndFlush(orcamentoA);

                mockMvc.perform(
                                get("/api/client/quotes/{id}", orcamentoA.getId())
                                        .header("Authorization", tokenClienteA))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.stage").value(etapa.exibida()));
            }

            for (var etapa :
                    List.of(
                            "Qualificação",
                            "Em análise pela área",
                            "Elaborando proposta",
                            "Em negociação")) {
                orcamentoA.setStage(etapa);
                orcamentos.saveAndFlush(orcamentoA);
                mockMvc.perform(
                                get("/api/client/quotes/{id}", orcamentoA.getId())
                                        .header("Authorization", tokenClienteA))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.stage").value(
                                EtapaAcompanhamentoOrcamento.deTexto(etapa).texto()));
            }
        }
    }

    @Test
    void criarEnsaioIniciaAcompanhamentoEEditarNaoReiniciaNemAceitaProposta() throws Exception {
        String dados = """
                {"newSample":{"quoteId":"%s","code":"A-INICIO","description":"Amostra"},
                 "type":"Análise de teste","analysisType":"chromatography",
                 "method":"Método","standard":"Norma","scheduledDate":"2026-11-01",
                 "responsibleId":"%s","equipmentIds":[],"measurements":[]}
                """.formatted(orcamentoA.getId(), tecnico.getId());
        String tokenTecnico = "Bearer " + loginInterno.execute(
                new LoginRequest(tecnico.getEmail(), " senha ")).accessToken();
        mockMvc.perform(post("/api/tests")
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content(dados))
                .andExpect(status().isCreated());
        var execucao = ensaios.findAll().stream()
                .filter(item -> item.getOrcamento().getId().equals(orcamentoA.getId()))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/api/client/quotes/{id}", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("Em execução"))
                .andExpect(jsonPath("$.status").value("PendingAcceptance"));

        execucao.setStatus(StatusEnsaio.VALIDATED);
        ensaios.saveAndFlush(execucao);
        String edicao = """
                {"sampleId":"%s","type":"Análise revisada","analysisType":"chromatography",
                 "method":"Método","standard":"Norma","scheduledDate":"2026-11-01",
                 "responsibleId":"%s","equipmentIds":[],"measurements":[],"version":%s}
                """.formatted(execucao.getAmostra().getId(), tecnico.getId(), execucao.getVersion());
        mockMvc.perform(put("/api/tests/{id}", execucao.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content(edicao))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/client/quotes/{id}", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("Validado"))
                .andExpect(jsonPath("$.status").value("PendingAcceptance"));
    }

    @Test
    void listaEDetalheUsamMenorEtapaAtivaSemMisturarOrcamentos() throws Exception {
        var incompleto = criarExecucao(orcamentoA, StatusEnsaio.INCOMPLETE);
        criarExecucao(orcamentoA, StatusEnsaio.FINALIZED);
        criarExecucao(orcamentoA, StatusEnsaio.CANCELLED);
        criarExecucao(orcamentoB, StatusEnsaio.COMPLETE);
        for (var statusEnsaio : List.of(StatusEnsaio.INCOMPLETE, StatusEnsaio.COMPLETE)) {
            incompleto.setStatus(statusEnsaio);
            ensaios.saveAndFlush(incompleto);
            String esperada = statusEnsaio == StatusEnsaio.INCOMPLETE ? "Em execução" : "Completo";
            mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].stage").value(esperada));
            mockMvc.perform(get("/api/client/quotes/{id}", orcamentoA.getId())
                            .header("Authorization", tokenClienteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stage").value(esperada));
        }
    }

    @Test
    void todasExecucoesCanceladasApresentamCanceladoSemAlterarComercial() throws Exception {
        criarExecucao(orcamentoA, StatusEnsaio.CANCELLED);
        mockMvc.perform(get("/api/client/quotes/{id}", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("Cancelado"))
                .andExpect(jsonPath("$.status").value("PendingAcceptance"));
    }

    @Test
    void contratoDeClienteNaoExpoeDisponibilidadeDeAnexoPersistido() throws Exception {
        documentos.deleteById(orcamentoA.getId());
        documentos.flush();

        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasDocument").doesNotExist());
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDocument").doesNotExist());
    }

    @Test
    void bloqueiaOrcamentoEPdfDeEmpresaSemVinculo() throws Exception {
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
    }

    @Test
    void orcamentoInexistenteRetornaNaoEncontrado() throws Exception {
        mockMvc.perform(get("/api/client/quotes/{id}", 999999L).header("Authorization", tokenClienteA))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizaAcessoQuandoVinculosDoClienteMudam() throws Exception {
        UpdateClienteRequest duasEmpresas =
                dadosAtualizacaoCliente(List.of(empresaA.getId(), empresaB.getId()), null);
        editarCliente.execute(clienteA.getId(), duasEmpresas);

        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoSemVinculo.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        editarCliente.execute(clienteA.getId(), dadosAtualizacaoCliente(List.of(empresaA.getId()), null));
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteSemVinculosRecebeListaVaziaSemAcessoDiretoAoOrcamento() throws Exception {
        jdbcTemplate.update("DELETE FROM cliente_empresas WHERE cliente_id = ?", clienteA.getId());
        entityManager.clear();

        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
    }

    @Test
    void desativacaoDeEmpresaRetiraAcessoEMantemLoginSemEmpresas() throws Exception {
        statusEmpresa.execute(empresaA.getId(), CadastroStatus.INACTIVE);
        var respostaLogin = loginCliente.execute(new LoginRequest("cliente-a@example.test", " senha "));

        assertTrue(respostaLogin.user().companies().isEmpty());
        assertEquals(1, clientes.findById(clienteA.getId()).orElseThrow().getEmpresas().size());
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
    }

    @Test
    void desativacaoDeClienteBloqueiaLoginEProximaRequisicaoComTokenAnterior() throws Exception {
        statusCliente.execute(clienteA.getId(), CadastroStatus.INACTIVE);
        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(new LoginRequest("cliente-a@example.test", " senha ")));
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNaoAcessaGestaoInterna() throws Exception {
        mockMvc.perform(get("/api/clients").header("Authorization", tokenClienteA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInternoNaoAcessaOrcamentosDeCliente() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenAdministrador))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void somenteAdministradorAcessaGestaoDeEmpresas() throws Exception {
        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(get("/api/companies").header("Authorization", tokenTecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/companies").header("Authorization", tokenAdministrador))
                .andExpect(status().isOk());
    }

    @Test
    void loginInternoRejeitaCliente() {
        LoginRequest credenciais = new LoginRequest("cliente-a@example.test", " senha ");

        assertThrows(CredenciaisInvalidasException.class, () -> loginInterno.execute(credenciais));
    }

    @Test
    void loginClienteRejeitaAdministradorETecnico() {
        LoginRequest credenciaisAdministrador = new LoginRequest(administrador.getEmail(), " senha ");
        LoginRequest credenciaisTecnico = new LoginRequest(tecnico.getEmail(), " senha ");

        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(credenciaisAdministrador));
        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(credenciaisTecnico));
    }

    @Test
    void buscaEmpresaComCnpjMascarado() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(
                        get("/api/companies")
                                .param("search", "00.000.001/0001-36")
                                .header("Authorization", tokenAdministrador))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("LOCAL"));
    }

    @Test
    void buscaClientePorEmpresaSemExporSenha() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(
                        get("/api/clients")
                                .param("search", "Empresa A")
                                .header("Authorization", tokenAdministrador))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist());
    }

    @Test
    void loginAceitaEmailComEspacosEMaiusculas() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":" CLIENTE-A@EXAMPLE.TEST ","password":" senha "}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("Client"))
                .andExpect(jsonPath("$.user.companies[0].id").value(empresaA.getId().toString()))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void senhaOmitidaPreservaHashESenhaNaoSofreTrim() {
        String hash = clienteA.getUsuario().getSenhaHash();
        editarCliente.execute(clienteA.getId(), dadosAtualizacaoCliente(List.of(empresaA.getId()), null));
        assertEquals(hash, clienteA.getUsuario().getSenhaHash());
        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(new LoginRequest("cliente-a@example.test", "senha")));
    }

    @Test
    void empresaInexistenteImpedeCriacaoDoCliente() {
        long quantidadeUsuariosAntes = usuarios.count();
        CreateClienteRequest dados = new CreateClienteRequest(
                "Novo cliente",
                "novo@example.test",
                "00000000000",
                "Contato",
                CadastroStatus.ACTIVE,
                List.of(empresaA.getId().toString(), "999999"),
                "senha");

        assertThrows(RegraNegocioException.class, () -> criarCliente.execute(dados));
        assertEquals(quantidadeUsuariosAntes, usuarios.count());
    }

    @Test
    void empresaInexistenteImpedeAlteracaoDosVinculos() {
        UpdateClienteRequest dados = dadosAtualizacaoCliente(List.of(empresaA.getId(), 999999L), null);

        assertThrows(
                RegraNegocioException.class,
                () -> editarCliente.execute(clienteA.getId(), dados));
        assertEquals(1, clienteA.getEmpresas().size());
        assertEquals(empresaA.getId(), clienteA.getEmpresas().iterator().next().getId());
    }

    @Test
    void rejeitaCnpjJaCadastrado() {
        CreateEmpresaRequest dados = dadosEmpresa("Duplicada", "00.000.001/0001-36");

        assertThrows(RegraNegocioException.class, () -> criarEmpresa.execute(dados));
    }

    @Test
    void rejeitaClienteComEmailDeAdministrador() {
        CreateClienteRequest dados = new CreateClienteRequest(
                "Cliente",
                administrador.getEmail().toUpperCase(),
                "00000000000",
                "Contato",
                CadastroStatus.ACTIVE,
                List.of(empresaA.getId().toString()),
                "senha");

        assertThrows(EmailJaCadastradoException.class, () -> criarCliente.execute(dados));
    }

    @Test
    void rejeitaStatusDeEmpresaInvalido() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(
                        patch("/api/companies/{id}/status", empresaA.getId())
                                .header("Authorization", tokenAdministrador)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"status":"INVALID"}
                                        """))
                .andExpect(status().isBadRequest());
    }

    private Empresa empresa(String nome, String cnpj) {
        return empresas.findById(Long.valueOf(criarEmpresa.execute(dadosEmpresa(nome, cnpj)).id()))
                .orElseThrow();
    }

    private CreateEmpresaRequest dadosEmpresa(String nome, String cnpj) {
        return new CreateEmpresaRequest(
                nome,
                cnpj,
                "Endereço Teste, 1",
                "Cidade Teste",
                "PR",
                "Contato",
                "00000000000",
                "contato@example.test",
                CadastroStatus.ACTIVE);
    }

    private Cliente cliente(String email, Empresa empresa) {
        var criado =
                criarCliente.execute(
                        new CreateClienteRequest(
                                email,
                                email,
                                "00000000000",
                                "Cargo",
                                CadastroStatus.ACTIVE,
                                List.of(empresa.getId().toString()),
                                " senha "));
        return clientes.findById(Long.valueOf(criado.id())).orElseThrow();
    }

    private UpdateClienteRequest dadosAtualizacaoCliente(List<Long> idsEmpresas, String senha) {
        return new UpdateClienteRequest(
                "Cliente A",
                "cliente-a@example.test",
                "00000000000",
                "Cargo",
                CadastroStatus.ACTIVE,
                idsEmpresas.stream().map(String::valueOf).toList(),
                senha);
    }

    private Usuario usuario(PerfilUsuario perfil, String email) {
        Usuario usuario = new Usuario();
        usuario.setPerfil(perfil);
        usuario.setEmail(email);
        usuario.setNome(perfil.name());
        usuario.setSenhaHash(passwordEncoder.encode(" senha "));
        return usuarios.saveAndFlush(usuario);
    }

    private Orcamento criarOrcamento(Empresa empresa, String codigo) {
        Orcamento orcamento = new Orcamento();
        orcamento.setEmpresa(empresa);
        orcamento.setCode(codigo);
        orcamento.setName(codigo);
        orcamento.setDescription("Análise de amostra");
        orcamento.setStage("Em negociação");
        orcamento.setStatus(StatusComercialOrcamento.PENDING_ACCEPTANCE);
        orcamento.setTotalPrice(BigDecimal.TEN);
        orcamento.setCreatedDate(OffsetDateTime.now());
        orcamento = orcamentos.saveAndFlush(orcamento);

        DocumentoOrcamento documento = new DocumentoOrcamento();
        documento.setOrcamento(orcamento);
        documento.setContent("%PDF-1.4 demo".getBytes());
        documentos.saveAndFlush(documento);
        return orcamento;
    }

    private ExecucaoEnsaio criarExecucao(Orcamento orcamento, StatusEnsaio status) {
        var amostra = new Amostra();
        amostra.setCodigo("A-" + UUID.randomUUID());
        amostra.setDescricao("Amostra do acompanhamento");
        amostra.setOrcamento(orcamento);
        amostras.saveAndFlush(amostra);
        var execucao = new ExecucaoEnsaio();
        execucao.setOrcamento(orcamento);
        execucao.setAmostra(amostra);
        execucao.setResponsavel(tecnico);
        execucao.setTipo("Análise");
        execucao.setMetodo("Método");
        execucao.setNorma("Norma");
        execucao.setDataAgendada(LocalDate.of(2026, 11, 1));
        execucao.setStatus(status);
        return ensaios.saveAndFlush(execucao);
    }

    private record StatusEsperado(StatusComercialOrcamento status, String stage) {}

    private record EtapaEsperada(String persistida, String exibida) {}
}
