package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import br.ufpr.tcc.backend_lab.application.dto.request.OrcamentoItemRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.OrcamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.UpdateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeClienteStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeEmpresaStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateEmpresaUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.LoginClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.LoginUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateOrcamentoUseCase;
import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoRelatorio;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.model.enums.EtapaAcompanhamentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoRelatorioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
    @Autowired RelatorioTecnicoRepository relatorios;
    @MockitoSpyBean DocumentoRelatorioRepository documentosRelatorios;
    @Autowired DocumentoOrcamentoRepository documentos;
    @Autowired CreateEmpresaUseCase criarEmpresa;
    @Autowired CreateClienteUseCase criarCliente;
    @Autowired UpdateClienteUseCase editarCliente;
    @Autowired UpdateOrcamentoUseCase editarOrcamento;
    @Autowired ChangeClienteStatusUseCase statusCliente;
    @Autowired ChangeEmpresaStatusUseCase statusEmpresa;
    @Autowired LoginClienteUseCase loginCliente;
    @Autowired LoginUseCase loginInterno;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper objectMapper;
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
    void clienteVeEtapaPublicadaSemMetadadosDuplicadosNaExecucao() throws Exception {
        Long relatorioId = criarRelatorioPublicado(orcamentoA, "A-RELATORIO-PUBLICADO");
        RelatorioTecnico relatorio = relatorios.findById(relatorioId).orElseThrow();
        ExecucaoEnsaio ensaio = ensaios.findById(relatorio.getExecucao().getId()).orElseThrow();
        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);

        mockMvc.perform(patch("/api/tests/{id}/status", ensaio.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"set-status\",\"targetStatus\":\"report-published\",\"version\":"
                                + ensaio.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("report-published"));

        ExecucaoEnsaio publicada = ensaios.findById(ensaio.getId()).orElseThrow();
        assertNull(publicada.getReportReference());
        assertNull(publicada.getPublishedAt());
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stage").value("Relatório publicado"));
        mockMvc.perform(get("/api/client/quotes/{id}", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("Relatório publicado"));

        var arquivo = mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();
        assertArrayEquals(documentosRelatorios.findByRelatorioId(relatorioId).orElseThrow().getContent(),
                arquivo.getResponse().getContentAsByteArray());
    }

    @Test
    void clienteBaixaSomenteRelatoriosPublicadosDasEmpresasVinculadas() throws Exception {
        Long relatorioAutorizado = criarRelatorioPublicado(orcamentoA, "A-RELATORIO-A");
        Long relatorioDeOutraEmpresa = criarRelatorioPublicado(orcamentoB, "A-RELATORIO-B");

        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(relatorioAutorizado.toString()))
                .andExpect(jsonPath("$[0].code").value(relatorioAutorizado.toString()))
                .andExpect(jsonPath("$[0].protocol").value(orcamentoA.getProtocol()))
                .andExpect(jsonPath("$[0].laboratory").value(orcamentoA.getLaboratoryName()))
                .andExpect(jsonPath("$[0].sample").value("A-RELATORIO-A - Amostra A-RELATORIO-A"))
                .andExpect(jsonPath("$[0].publishedOn").isNotEmpty());

        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.reports[0].id").value(relatorioAutorizado.toString()))
                .andExpect(jsonPath("$.reports[0].testType").value("Análise cromatográfica"))
                .andExpect(jsonPath("$.protocol").value(orcamentoA.getProtocol()))
                .andExpect(jsonPath("$.testType").value("Análise cromatográfica"))
                .andExpect(jsonPath("$.summary").value("1 relatório publicado disponível para consulta e download."));

        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoB.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioDeOutraEmpresa)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());

        var arquivo = mockMvc.perform(get("/api/client/reports/{id}/file", relatorioAutorizado)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();
        assertArrayEquals(documentosRelatorios.findByRelatorioId(relatorioAutorizado).orElseThrow().getContent(),
                arquivo.getResponse().getContentAsByteArray());
    }

    @Test
    void resumeSomenteRelatoriosPublicadosAcessiveisComTiposDistintos() throws Exception {
        Long fisicoQuimica = criarRelatorioPublicado(orcamentoA, "AMOSTRA-RESUMO-FQ-1",
                "Análise físico-química");
        Long cromatografia = criarRelatorioPublicado(orcamentoA, "AMOSTRA-RESUMO-CROM-1",
                "Análise cromatográfica");
        Long fisicoQuimicaRepetido = criarRelatorioPublicado(orcamentoA, "AMOSTRA-RESUMO-FQ-2",
                "Análise físico-química");
        Long pdfInvalido = criarRelatorioPublicado(orcamentoA, "AMOSTRA-RESUMO-PDF-INVALIDO",
                "Análise sem PDF válido");
        criarRelatorioPendente(orcamentoA, "AMOSTRA-RESUMO-RASCUNHO");
        criarRelatorioPublicado(orcamentoB, "AMOSTRA-RESUMO-OUTRA-EMPRESA",
                "Análise de outra empresa");

        OffsetDateTime instantePublicacao = OffsetDateTime.parse("2026-11-08T12:00:00Z");
        for (Long id : List.of(fisicoQuimica, cromatografia, fisicoQuimicaRepetido, pdfInvalido)) {
            jdbcTemplate.update("UPDATE relatorios_tecnicos SET published_at = ? WHERE id = ?", instantePublicacao, id);
        }
        DocumentoRelatorio documentoInvalido = documentosRelatorios.findByRelatorioId(pdfInvalido).orElseThrow();
        documentoInvalido.setContent("conteúdo que não é PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        documentosRelatorios.saveAndFlush(documentoInvalido);
        entityManager.clear();

        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(3))
                .andExpect(jsonPath("$.reports[0].testType").value("Análise físico-química"))
                .andExpect(jsonPath("$.reports[1].testType").value("Análise cromatográfica"))
                .andExpect(jsonPath("$.reports[2].testType").value("Análise físico-química"))
                .andExpect(jsonPath("$.testType").value("Análise físico-química; Análise cromatográfica"))
                .andExpect(jsonPath("$.summary")
                        .value("3 relatórios publicados disponíveis para consulta e download."));

        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void retornaResumoVazioParaOrcamentoAutorizadoSemRelatorios() throws Exception {
        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocol").value(orcamentoA.getProtocol()))
                .andExpect(jsonPath("$.testType").value(""))
                .andExpect(jsonPath("$.summary")
                        .value("Nenhum relatório publicado disponível para consulta e download."))
                .andExpect(jsonPath("$.reports.length()").value(0));

        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/client/quotes/{id}/reports", 999999L)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isNotFound());
    }

    @Test
    void mantemResumoEIdentificacaoDoSnapshotValidadoAposEdicao() throws Exception {
        String protocoloValidado = orcamentoA.getProtocol();
        String laboratorioValidado = orcamentoA.getLaboratoryName();
        String tipoValidado = "Análise físico-química do óleo";
        Long relatorioId = criarRelatorioPublicado(orcamentoA, "AMOSTRA-SNAPSHOT-ORIGINAL", tipoValidado);
        RelatorioTecnico relatorio = relatorios.findById(relatorioId).orElseThrow();
        Long execucaoId = relatorio.getExecucao().getId();

        jdbcTemplate.update("UPDATE execucoes_ensaios SET tipo = ? WHERE id = ?",
                "Tipo editado depois da validação", execucaoId);
        jdbcTemplate.update("UPDATE amostras SET codigo = ?, descricao = ? WHERE id = ?",
                "AMOSTRA-SNAPSHOT-EDITADA", "Descrição editada depois da validação",
                relatorio.getExecucao().getAmostra().getId());
        jdbcTemplate.update("UPDATE orcamentos SET protocol = ?, laboratory_name = ? WHERE id = ?",
                "L-EDITADO-DEPOIS-VALIDACAO", "Laboratório editado depois da validação", orcamentoA.getId());
        entityManager.clear();

        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocol").value(protocoloValidado))
                .andExpect(jsonPath("$.testType").value(tipoValidado))
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.reports[0].protocol").value(protocoloValidado))
                .andExpect(jsonPath("$.reports[0].laboratory").value(laboratorioValidado))
                .andExpect(jsonPath("$.reports[0].sample")
                        .value("AMOSTRA-SNAPSHOT-ORIGINAL - Amostra AMOSTRA-SNAPSHOT-ORIGINAL"))
                .andExpect(jsonPath("$.reports[0].testType").value(tipoValidado))
                .andExpect(jsonPath("$.summary")
                        .value("1 relatório publicado disponível para consulta e download."));
    }

    @Test
    void mantemVinculosDoDocumentoValidadoQuandoExecucaoMudaDeOrcamento() throws Exception {
        Long relatorioId = criarRelatorioValidado(orcamentoA, "AMOSTRA-ORIGINAL");
        RelatorioTecnico relatorio = relatorios.findById(relatorioId).orElseThrow();
        ExecucaoEnsaio ensaio = ensaios.findById(relatorio.getExecucao().getId()).orElseThrow();
        Amostra amostraDaEmpresaB = new Amostra();
        amostraDaEmpresaB.setCodigo("AMOSTRA-NOVA");
        amostraDaEmpresaB.setDescricao("Amostra da empresa B");
        amostraDaEmpresaB.setOrcamento(orcamentoB);
        amostraDaEmpresaB = amostras.saveAndFlush(amostraDaEmpresaB);

        ensaio.setAmostra(amostraDaEmpresaB);
        ensaio.setOrcamento(orcamentoB);
        ensaios.saveAndFlush(ensaio);
        entityManager.clear();

        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        mockMvc.perform(get("/api/reports/{id}", relatorioId).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.id").value(empresaA.getId().toString()))
                .andExpect(jsonPath("$.quote.id").value(orcamentoA.getId().toString()))
                .andExpect(jsonPath("$.sample.code").value("AMOSTRA-ORIGINAL"));

        relatorio = relatorios.findById(relatorioId).orElseThrow();
        mockMvc.perform(post("/api/reports/{id}/publication", relatorioId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + relatorio.getVersion() + "}"))
                .andExpect(status().isOk());

        Cliente clienteB = cliente("cliente-b@example.test", empresaB);
        String tokenClienteB = "Bearer " + loginCliente.execute(
                new LoginRequest(clienteB.getUsuario().getEmail(), " senha ")).accessToken();

        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(relatorioId.toString()))
                .andExpect(jsonPath("$[0].sample").value("AMOSTRA-ORIGINAL - Amostra AMOSTRA-ORIGINAL"));
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].id").value(relatorioId.toString()));
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteB))
                .andExpect(status().isForbidden());

        ensaio = ensaios.findById(ensaio.getId()).orElseThrow();
        mockMvc.perform(patch("/api/tests/{id}/status", ensaio.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"set-status\",\"targetStatus\":\"report-published\",\"version\":"
                                + ensaio.getVersion() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("published-report-unavailable"));
    }

    @Test
    void mantemVinculosDoDocumentoPublicadoQuandoExecucaoMudaDeOrcamento() throws Exception {
        Long relatorioId = criarRelatorioPublicado(orcamentoA, "AMOSTRA-ORIGINAL-APOS-PUBLICACAO");
        RelatorioTecnico relatorio = relatorios.findById(relatorioId).orElseThrow();
        ExecucaoEnsaio ensaio = ensaios.findById(relatorio.getExecucao().getId()).orElseThrow();
        Amostra amostraDaEmpresaB = new Amostra();
        amostraDaEmpresaB.setCodigo("AMOSTRA-B-APOS-PUBLICACAO");
        amostraDaEmpresaB.setDescricao("Amostra B após publicação");
        amostraDaEmpresaB.setOrcamento(orcamentoB);
        amostraDaEmpresaB = amostras.saveAndFlush(amostraDaEmpresaB);
        ensaio.setAmostra(amostraDaEmpresaB);
        ensaio.setOrcamento(orcamentoB);
        ensaios.saveAndFlush(ensaio);
        entityManager.clear();

        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        mockMvc.perform(get("/api/reports/{id}", relatorioId).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.id").value(empresaA.getId().toString()))
                .andExpect(jsonPath("$.quote.id").value(orcamentoA.getId().toString()))
                .andExpect(jsonPath("$.sample.code").value("AMOSTRA-ORIGINAL-APOS-PUBLICACAO"));

        Cliente clienteB = cliente("cliente-b-pos-publicacao@example.test", empresaB);
        String tokenClienteB = "Bearer " + loginCliente.execute(
                new LoginRequest(clienteB.getUsuario().getEmail(), " senha ")).accessToken();
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(relatorioId.toString()))
                .andExpect(jsonPath("$[0].sample")
                        .value("AMOSTRA-ORIGINAL-APOS-PUBLICACAO - Amostra AMOSTRA-ORIGINAL-APOS-PUBLICACAO"));
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteB))
                .andExpect(status().isForbidden());

        ensaio = ensaios.findById(ensaio.getId()).orElseThrow();
        mockMvc.perform(patch("/api/tests/{id}/status", ensaio.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"set-status\",\"targetStatus\":\"report-published\",\"version\":"
                                + ensaio.getVersion() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("published-report-unavailable"));
    }

    @Test
    void naoEntregaPorOrcamentoQuandoEmpresaDoMesmoOrcamentoMuda() throws Exception {
        Long relatorioId = criarRelatorioPublicado(orcamentoA, "AMOSTRA-EMPRESA-ORCAMENTO-TROCADA");
        OrcamentoRequest dados = requestAceite(orcamentoA);
        OrcamentoRequest transferido = new OrcamentoRequest(dados.code(), empresaB.getId().toString(),
                dados.name(), dados.laboratoryName(), dados.protocol(), dados.description(), dados.scope(),
                dados.externalContactName(), dados.externalContactEmail(), dados.status(),
                dados.totalPrice(), dados.items());
        editarOrcamento.execute(orcamentoA.getId(), transferido, null);
        entityManager.clear();

        Cliente clienteB = cliente("cliente-b-orcamento-trocado@example.test", empresaB);
        String tokenClienteB = "Bearer " + loginCliente.execute(
                new LoginRequest(clienteB.getUsuario().getEmail(), " senha ")).accessToken();
        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(0));
        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(relatorioId.toString()));
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteB))
                .andExpect(status().isForbidden());

        RelatorioTecnico relatorio = relatorios.findById(relatorioId).orElseThrow();
        ExecucaoEnsaio ensaio = ensaios.findById(relatorio.getExecucao().getId()).orElseThrow();
        mockMvc.perform(patch("/api/tests/{id}/status", ensaio.getId())
                        .header("Authorization", "Bearer " + jwtService.generateToken(tecnico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"set-status\",\"targetStatus\":\"report-published\",\"version\":"
                                + ensaio.getVersion() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("published-report-unavailable"));
    }

    @Test
    void mantemEmpresaValidadaQuandoMesmoOrcamentoMudaAntesDaPublicacao() throws Exception {
        Long relatorioId = criarRelatorioValidado(orcamentoA, "AMOSTRA-VALIDADA-ANTES-TROCA-EMPRESA");
        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        OrcamentoRequest dados = requestAceite(orcamentoA);
        OrcamentoRequest transferido = new OrcamentoRequest(dados.code(), empresaB.getId().toString(),
                dados.name(), dados.laboratoryName(), dados.protocol(), dados.description(), dados.scope(),
                dados.externalContactName(), dados.externalContactEmail(), dados.status(),
                dados.totalPrice(), dados.items());
        editarOrcamento.execute(orcamentoA.getId(), transferido, null);
        entityManager.clear();

        Cliente clienteB = cliente("cliente-b-orcamento-trocado-antes-publicacao@example.test", empresaB);
        String tokenClienteB = "Bearer " + loginCliente.execute(
                new LoginRequest(clienteB.getUsuario().getEmail(), " senha ")).accessToken();

        mockMvc.perform(get("/api/reports/{id}", relatorioId).header("Authorization", tokenTecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.id").value(empresaA.getId().toString()))
                .andExpect(jsonPath("$.quote.id").value(orcamentoA.getId().toString()));
        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(0));
        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/reports/{id}/publication", relatorioId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(relatorioId.toString()));
        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/api/client/reports/{id}/file", relatorioId)
                        .header("Authorization", tokenClienteB))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejeitaValidacaoQuandoConteudoMudaDepoisDaLeituraDoRelatorio() throws Exception {
        Long relatorioId = criarRelatorioPendente(orcamentoA, "AMOSTRA-REVISAO");
        RelatorioTecnico relatorio = relatorios.findById(relatorioId).orElseThrow();
        ExecucaoEnsaio ensaio = ensaios.findById(relatorio.getExecucao().getId()).orElseThrow();
        ensaio.setTextoLaudo("Conclusão atualizada depois da revisão");
        ensaios.saveAndFlush(ensaio);

        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        mockMvc.perform(post("/api/reports/{id}/validation", relatorioId)
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"contentFingerprint\":\"0000000000000000000000000000000000000000000000000000000000000000\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("report-content-changed"));

        assertEquals(StatusValidacaoRelatorio.PENDING_VALIDATION,
                relatorios.findById(relatorioId).orElseThrow().getValidationStatus());
        assertFalse(documentosRelatorios.existsById(relatorioId));
    }

    private Long criarRelatorioPublicado(Orcamento orcamento, String codigoAmostra) throws Exception {
        return criarRelatorioPublicado(orcamento, codigoAmostra, "Análise cromatográfica");
    }

    private Long criarRelatorioPublicado(Orcamento orcamento, String codigoAmostra, String tipoEnsaio)
            throws Exception {
        Amostra amostra = new Amostra();
        amostra.setCodigo(codigoAmostra);
        amostra.setDescricao("Amostra " + codigoAmostra);
        amostra.setOrcamento(orcamento);
        amostra = amostras.saveAndFlush(amostra);

        ExecucaoEnsaio ensaio = new ExecucaoEnsaio();
        ensaio.setCodigo("ER-" + UUID.randomUUID().toString().substring(0, 24));
        ensaio.setAmostra(amostra);
        ensaio.setOrcamento(orcamento);
        ensaio.setResponsavel(tecnico);
        ensaio.setTipo(tipoEnsaio);
        ensaio.setTipoAnalise("chromatography");
        ensaio.setMetodo("Método registrado");
        ensaio.setNorma("Norma registrada");
        ensaio.setDataAgendada(LocalDate.of(2026, 11, 7));
        ensaio.setStatus(StatusEnsaio.DRAFTING_REPORT);
        ensaio = ensaios.saveAndFlush(ensaio);

        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        mockMvc.perform(post("/api/tests/{id}/reports/preparation", ensaio.getId())
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isCreated());
        RelatorioTecnico relatorio = relatorios.findByExecucaoIdOrderByIdAsc(ensaio.getId()).getFirst();
        validarRelatorio(relatorio, tokenTecnico);
        mockMvc.perform(post("/api/reports/{id}/publication", relatorio.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk());
        return relatorio.getId();
    }

    private Long criarRelatorioValidado(Orcamento orcamento, String codigoAmostra) throws Exception {
        return criarRelatorioValidado(orcamento, codigoAmostra, "Análise cromatográfica");
    }

    private Long criarRelatorioValidado(Orcamento orcamento, String codigoAmostra, String tipoEnsaio)
            throws Exception {
        Long reportId = criarRelatorioPendente(orcamento, codigoAmostra, tipoEnsaio);
        RelatorioTecnico relatorio = relatorios.findById(reportId).orElseThrow();
        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        validarRelatorio(relatorio, tokenTecnico);
        return reportId;
    }

    private Long criarRelatorioPendente(Orcamento orcamento, String codigoAmostra) throws Exception {
        return criarRelatorioPendente(orcamento, codigoAmostra, "Análise cromatográfica");
    }

    private Long criarRelatorioPendente(Orcamento orcamento, String codigoAmostra, String tipoEnsaio)
            throws Exception {
        Amostra amostra = new Amostra();
        amostra.setCodigo(codigoAmostra);
        amostra.setDescricao("Amostra " + codigoAmostra);
        amostra.setOrcamento(orcamento);
        amostra = amostras.saveAndFlush(amostra);

        ExecucaoEnsaio ensaio = new ExecucaoEnsaio();
        ensaio.setCodigo("ER-" + UUID.randomUUID().toString().substring(0, 24));
        ensaio.setAmostra(amostra);
        ensaio.setOrcamento(orcamento);
        ensaio.setResponsavel(tecnico);
        ensaio.setTipo(tipoEnsaio);
        ensaio.setTipoAnalise("chromatography");
        ensaio.setMetodo("Método registrado");
        ensaio.setNorma("Norma registrada");
        ensaio.setDataAgendada(LocalDate.of(2026, 11, 7));
        ensaio.setStatus(StatusEnsaio.DRAFTING_REPORT);
        ensaio = ensaios.saveAndFlush(ensaio);

        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        mockMvc.perform(post("/api/tests/{id}/reports/preparation", ensaio.getId())
                        .header("Authorization", tokenTecnico))
                .andExpect(status().isCreated());
        RelatorioTecnico relatorio = relatorios.findByExecucaoIdOrderByIdAsc(ensaio.getId()).getFirst();
        return relatorio.getId();
    }

    private void validarRelatorio(RelatorioTecnico relatorio, String tokenTecnico) throws Exception {
        String fingerprint = fingerprintRelatorio(relatorio.getId(), tokenTecnico);
        mockMvc.perform(post("/api/reports/{id}/validation", relatorio.getId())
                        .header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + relatorio.getVersion()
                                + ",\"contentFingerprint\":\"" + fingerprint + "\"}"))
                .andExpect(status().isOk());
    }

    private String fingerprintRelatorio(Long relatorioId, String token) throws Exception {
        String detalhe = mockMvc.perform(get("/api/reports/{id}", relatorioId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(detalhe).get("contentFingerprint").asText();
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
    void listaClienteSemCarregarBytesDosPdfsDeCadaRelatorio() throws Exception {
        criarRelatorioPublicado(orcamentoA, "AMOSTRA-LOTE-CLIENTE-A");
        criarRelatorioPublicado(orcamentoA, "AMOSTRA-LOTE-CLIENTE-B");
        clearInvocations(documentosRelatorios);

        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(documentosRelatorios, never()).findByRelatorioId(any());
        verify(documentosRelatorios, times(1)).buscarIdsComPdfValido(anyCollection());

        clearInvocations(documentosRelatorios);
        mockMvc.perform(get("/api/client/quotes/{id}/reports", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(2));
        verify(documentosRelatorios, never()).findByRelatorioId(any());
        verify(documentosRelatorios, times(1)).buscarIdsComPdfValido(anyCollection());
    }

    @Test
    void omiteDocumentoPublicoComPdfInvalido() throws Exception {
        Long reportId = criarRelatorioPublicado(orcamentoA, "AMOSTRA-PDF-INVALIDO");
        DocumentoRelatorio documento = documentosRelatorios.findByRelatorioId(reportId).orElseThrow();
        documento.setContent("conteúdo que não é PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        documentosRelatorios.saveAndFlush(documento);

        mockMvc.perform(get("/api/client/reports").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/client/reports/{id}/file", reportId)
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isNotFound());
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
    void criarEnsaioIniciaAcompanhamentoSemAlterarStatusComercial() throws Exception {
        orcamentoA.setStatus(StatusComercialOrcamento.IN_PROGRESS);
        orcamentoA.setStage("Recebido");
        orcamentos.saveAndFlush(orcamentoA);
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
                .andExpect(jsonPath("$.status").value("InProgress"));

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
                .andExpect(jsonPath("$.status").value("InProgress"));
    }

    @Test
    void aceiteSemExecucaoApresentaRecebidoEPreservaEtapasPreExecucaoValidas() throws Exception {
        editarOrcamento.execute(orcamentoA.getId(), requestAceite(orcamentoA), null);
        mockMvc.perform(get("/api/client/quotes/{id}", orcamentoA.getId())
                        .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("Recebido"))
                .andExpect(jsonPath("$.status").value("InProgress"));
        assertEquals(0, ensaios.count());

        for (String etapa : List.of(
                "Aprovado pelo cliente",
                "Aguardando entrega da amostra",
                "Protocolado")) {
            orcamentoA.setStatus(StatusComercialOrcamento.PENDING_ACCEPTANCE);
            orcamentoA.setStage(etapa);
            orcamentos.saveAndFlush(orcamentoA);
            editarOrcamento.execute(orcamentoA.getId(), requestAceite(orcamentoA), null);

            mockMvc.perform(get("/api/client/quotes/{id}", orcamentoA.getId())
                            .header("Authorization", tokenClienteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stage").value(etapa))
                    .andExpect(jsonPath("$.status").value("InProgress"));
        }
        assertEquals(0, ensaios.count());
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
        orcamento.setProtocol("L-TEST-2026-" + codigo);
        orcamento.setLaboratoryName("Laboratório de teste");
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

    private OrcamentoRequest requestAceite(Orcamento orcamento) {
        return new OrcamentoRequest(
                orcamento.getCode(),
                orcamento.getEmpresa().getId().toString(),
                orcamento.getName(),
                "Laboratório de teste",
                "L-TEST-2026-" + orcamento.getCode(),
                orcamento.getDescription(),
                null,
                "Contato de teste",
                "contato@example.test",
                StatusComercialOrcamento.IN_PROGRESS.codigo(),
                BigDecimal.TEN,
                List.of(new OrcamentoItemRequest("Ensaio", BigDecimal.ONE, "ABNT", BigDecimal.TEN)));
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
