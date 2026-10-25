package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrcamentoControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired DocumentoOrcamentoRepository documentos;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    private Empresa copel;
    private Empresa sanepar;
    private Orcamento orcamentoMaisNovo;
    private Orcamento orcamentoMesmoDia;
    private Orcamento orcamentoAntigo;
    private Orcamento orcamentoComPercentual;
    private String tokenAdministrador;
    private String tokenTecnico;
    private String tokenCliente;

    @BeforeEach
    void prepararDados() {
        copel = criarEmpresa("Copel Distribuição", "00000001000136");
        sanepar = criarEmpresa("Sanepar", "00000002000180");
        tokenAdministrador = token(PerfilUsuario.ADMIN, "admin-orcamentos@example.test");
        tokenTecnico = token(PerfilUsuario.TECNICO, "tecnico-orcamentos@example.test");
        tokenCliente = token(PerfilUsuario.CLIENTE, "cliente-orcamentos@example.test");

        OffsetDateTime mesmaData = OffsetDateTime.parse("2026-10-15T10:00:00Z");
        orcamentoMaisNovo =
                criarOrcamento(
                        copel,
                        "EAQ_2026_45482_V_1",
                        "Análise de óleo",
                        "Análise físico-química de óleo isolante 100%",
                        StatusComercialOrcamento.PENDING_ACCEPTANCE,
                        mesmaData);
        orcamentoMesmoDia =
                criarOrcamento(
                        sanepar,
                        "EAQ_2026_33322_V_3",
                        "Ensaio de curto-circuito",
                        "Compactação de amostras",
                        StatusComercialOrcamento.IN_PROGRESS,
                        mesmaData);
        orcamentoAntigo =
                criarOrcamento(
                        copel,
                        "EAQ_2026_11111_V_1",
                        "Relatório de água",
                        "Análise de água",
                        StatusComercialOrcamento.CANCELLED,
                        OffsetDateTime.parse("2026-10-10T10:00:00Z"));
        orcamentoComPercentual =
                criarOrcamento(
                        sanepar,
                        "EAQX2026X45482XVX1",
                        "Análise de 100 amostras",
                        "Análise de 100 amostras",
                        StatusComercialOrcamento.FINISHED,
                        OffsetDateTime.parse("2026-10-09T10:00:00Z"));

        DocumentoOrcamento documento = new DocumentoOrcamento();
        documento.setOrcamento(orcamentoMaisNovo);
        documento.setContent("%PDF-1.4 conteúdo de teste".getBytes());
        documentos.saveAndFlush(documento);
    }

    @Test
    @DisplayName("lista orçamentos em páginas ordenadas por data e identificador")
    void listaOrcamentosComPaginacaoEOrdemEstavel() throws Exception {
        mockMvc.perform(
                        get("/api/quotes")
                                .param("page", "1")
                                .param("pageSize", "2")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(orcamentoMesmoDia.getId().toString()))
                .andExpect(jsonPath("$.items[0].code").value("EAQ_2026_33322_V_3"))
                .andExpect(jsonPath("$.items[0].hasDocument").value(false))
                .andExpect(jsonPath("$.items[1].id").value(orcamentoMaisNovo.getId().toString()))
                .andExpect(jsonPath("$.items[1].hasDocument").value(true));

        mockMvc.perform(
                        get("/api/quotes")
                                .param("page", "2")
                                .param("pageSize", "2")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(orcamentoAntigo.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(orcamentoComPercentual.getId().toString()));

        mockMvc.perform(
                        get("/api/quotes")
                                .param("page", "3")
                                .param("pageSize", "2")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("pesquisa por código, nome, descrição ou empresa e filtra por status")
    void pesquisaOrcamentosPorTextoEmpresaENomeComFiltroDeStatus() throws Exception {
        executarBusca("EAQ_2026_45482_V_1")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value("EAQ_2026_45482_V_1"));

        executarBusca("Análise físico-química")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Análise de óleo"));

        executarBusca("circuito")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Ensaio de curto-circuito"));

        executarBusca("  cOpEl  ")
                .andExpect(jsonPath("$.totalItems").value(2));

        executarBusca("100%")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(orcamentoMaisNovo.getId().toString()));

        executarBusca("Copel")
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].companyName").value("Copel Distribuição"));

        mockMvc.perform(
                        get("/api/quotes")
                                .param("status", "PendingAcceptance")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PendingAcceptance"));

        mockMvc.perform(
                        get("/api/quotes")
                                .param("search", "Copel")
                                .param("status", "PendingAcceptance")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].code").value("EAQ_2026_45482_V_1"));
    }

    @Test
    @DisplayName("rejeita status fora dos códigos comerciais")
    void rejeitaStatusForaDosCodigosComerciais() throws Exception {
        mockMvc.perform(
                        get("/api/quotes")
                                .param("status", "Aguardando revisão")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("inclui orçamentos de empresas inativas na pesquisa administrativa")
    void pesquisaAdministrativaIncluiEmpresaInativa() throws Exception {
        copel.setAtivo(false);
        empresas.saveAndFlush(copel);

        executarBusca("Copel")
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(
                        jsonPath("$.items[*].companyName")
                                .value(everyItem(is("Copel Distribuição"))));
    }

    @Test
    @DisplayName("rejeita status e parâmetros de paginação inválidos")
    void rejeitaFiltrosEPaginacaoInvalidos() throws Exception {
        mockMvc.perform(
                        get("/api/quotes")
                                .param("status", "Validated")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/quotes")
                                .param("page", "0")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/quotes")
                                .param("page", "-1")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/quotes")
                                .param("pageSize", "0")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/quotes")
                                .param("pageSize", "101")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/quotes")
                                .param("page", "21474838")
                                .param("pageSize", "100")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("permite consulta interna apenas ao administrador")
    void restringeConsultaDeOrcamentosAoAdministrador() throws Exception {
        mockMvc.perform(get("/api/quotes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/api/quotes")
                                .header("Authorization", tokenTecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/quotes")
                                .header("Authorization", tokenCliente))
                .andExpect(status().isUnauthorized());

    }

    @Test
    @DisplayName("consulta detalhes e baixa documento sem expor a consulta de cliente")
    void consultaDetalheEDocumentoDoOrcamento() throws Exception {
        mockMvc.perform(
                        get("/api/quotes/{id}", orcamentoMaisNovo.getId())
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orcamentoMaisNovo.getId().toString()))
                .andExpect(jsonPath("$.companyName").value("Copel Distribuição"))
                .andExpect(jsonPath("$.companyId").value(copel.getId().toString()))
                .andExpect(jsonPath("$.laboratoryName").value(nullValue()))
                .andExpect(jsonPath("$.scope").value(nullValue()))
                .andExpect(jsonPath("$.externalContactName").value("Contato"))
                .andExpect(jsonPath("$.externalContactEmail").value("contato@example.test"))
                .andExpect(jsonPath("$.hasDocument").value(true))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(
                        get("/api/quotes/{id}/document", orcamentoMaisNovo.getId())
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes("%PDF-1.4 conteúdo de teste".getBytes()));

        mockMvc.perform(
                        get("/api/quotes/{id}/document", orcamentoMesmoDia.getId())
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get("/api/quotes/{id}", 999999L)
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get("/api/quotes/{id}", orcamentoMesmoDia.getId())
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDocument").value(false));

        mockMvc.perform(
                        get("/api/quotes")
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].items").doesNotExist());

        mockMvc.perform(get("/api/quotes/{id}", orcamentoMaisNovo.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/api/quotes/{id}", orcamentoMaisNovo.getId())
                                .header("Authorization", tokenTecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/quotes/{id}", orcamentoMaisNovo.getId())
                                .header("Authorization", tokenCliente))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/api/quotes/{id}/document", orcamentoMaisNovo.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        get("/api/quotes/{id}/document", orcamentoMaisNovo.getId())
                                .header("Authorization", tokenTecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/quotes/{id}/document", orcamentoMaisNovo.getId())
                                .header("Authorization", tokenCliente))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cria orçamento em JSON com contato próprio e itens calculados")
    void criaOrcamentoJsonComContatoProprioEItens() throws Exception {
        String corpo = corpoOrcamento("NOVO-001", copel, "PendingAcceptance", "2", "15.00", "30.00");

        mockMvc.perform(
                        post("/api/quotes")
                                .header("Authorization", tokenAdministrador)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("NOVO-001"))
                .andExpect(jsonPath("$.status").value("PendingAcceptance"))
                .andExpect(jsonPath("$.stage").value("Negociação"))
                .andExpect(jsonPath("$.companyId").value(copel.getId().toString()))
                .andExpect(jsonPath("$.laboratoryName").value("Laboratório de Materiais"))
                .andExpect(jsonPath("$.scope").value("Ensaios de caracterização"))
                .andExpect(jsonPath("$.externalContactName").value("Contato do orçamento"))
                .andExpect(jsonPath("$.externalContactEmail").value("novo.contato@example.test"))
                .andExpect(jsonPath("$.totalPrice").value(30.00))
                .andExpect(jsonPath("$.hasDocument").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].testType").value("Resistência à tração"))
                .andExpect(jsonPath("$.items[0].sampleCount").value(2))
                .andExpect(jsonPath("$.items[0].standard").value("ABNT NBR 1234"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(15.00));

        executarBusca("NOVO-001")
                .andExpect(jsonPath("$.items[0].items").doesNotExist());
    }

    @Test
    @DisplayName("cria e atualiza orçamento multipart preservando PDF maior que 255 bytes")
    void criaEAtualizaOrcamentoMultipartComPdfGrande() throws Exception {
        byte[] pdfOriginal = pdfDeTeste(700);
        MockMultipartFile quote = parteJson(corpoOrcamento(
                "MULTIPART-001", copel, "PendingAcceptance", "2", "15.00", "30.00"));
        MockMultipartFile pdf = new MockMultipartFile(
                "document", "proposta.pdf", MediaType.APPLICATION_PDF_VALUE, pdfOriginal);

        String corpoCriacao = mockMvc.perform(
                        multipart("/api/quotes")
                                .file(quote)
                                .file(pdf)
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasDocument").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(corpoCriacao)
                .get("id")
                .asText();

        mockMvc.perform(
                        get("/api/quotes/{id}/document", id)
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdfOriginal));
        assertTrue(documentos.findById(Long.valueOf(id)).orElseThrow().getContent().length > 255);

        String dataEdicao = corpoOrcamento(
                "MULTIPART-001", copel, "InProgress", "1", "60.00", "60.00");
        byte[] pdfNovo = pdfDeTeste(900);
        mockMvc.perform(
                        multipart("/api/quotes/{id}", id)
                                .file(parteJson(dataEdicao))
                                .file(new MockMultipartFile(
                                        "document", "substituto.pdf", MediaType.APPLICATION_PDF_VALUE, pdfNovo))
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("InProgress"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sampleCount").value(1));

        mockMvc.perform(
                        get("/api/quotes/{id}/document", id)
                                .header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdfNovo));
        assertTrue(documentos.findById(Long.valueOf(id)).orElseThrow().getContent().length > 255);
    }

    @Test
    @DisplayName("atualiza orçamento preservando etapa, data e documento quando omitido")
    void atualizaOrcamentoSemDocumentoPreservaMetadados() throws Exception {
        String etapa = "Aguardando análise técnica";
        orcamentoMaisNovo.setStage(etapa);
        orcamentos.saveAndFlush(orcamentoMaisNovo);
        byte[] pdfAntes = documentos.findById(orcamentoMaisNovo.getId()).orElseThrow().getContent();

        mockMvc.perform(
                        put("/api/quotes/{id}", orcamentoMaisNovo.getId())
                                .header("Authorization", tokenAdministrador)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpoOrcamento(
                                        "EAQ_2026_45482_V_1", copel, "InProgress", "3", "10.00", "30.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value(etapa))
                .andExpect(jsonPath("$.status").value("InProgress"))
                .andExpect(jsonPath("$.createdDate").value("2026-10-15T10:00:00Z"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sampleCount").value(3))
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.00));

        assertArrayEquals(
                pdfAntes, documentos.findById(orcamentoMaisNovo.getId()).orElseThrow().getContent());
    }

    @Test
    @DisplayName("rejeita dados de itens inválidos sem converter quantidades ou arredondar preços")
    void rejeitaItensFracionariosValoresComPrecisaoExcessivaENulos() throws Exception {
        String quantidadeFracionaria = corpoOrcamento("INVALIDO-1", copel, "PendingAcceptance", "1.5", "10.00", "10.00");
        String precoFracionario = corpoOrcamento("INVALIDO-2", copel, "PendingAcceptance", "1", "10.001", "10.00");
        String itemNulo = quantidadeFracionaria.replace("\"items\": [{", "\"items\": [null, {");

        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON).content(quantidadeFracionaria))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON).content(precoFracionario))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON).content(itemNulo))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("INVALIDO-4", copel, "PendingAcceptance", "2", "10.00", "10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("valida status, empresa, documento, duplicidade e perfil ao salvar")
    void validaRegrasDeCriacaoDeOrcamento() throws Exception {
        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("DUPLICADO", copel, "PendingAcceptance", "1", "10.00", "10.00")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("DUPLICADO", sanepar, "PendingAcceptance", "1", "10.00", "10.00")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("STATUS-INV", copel, "InvalidStatus", "1", "10.00", "10.00")))
                .andExpect(status().isBadRequest());

        copel.setAtivo(false);
        empresas.saveAndFlush(copel);
        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("EMPRESA-INATIVA", copel, "PendingAcceptance", "1", "10.00", "10.00")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/quotes").header("Authorization", tokenAdministrador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("PDF-EXT", sanepar, "PendingAcceptance", "1", "10.00", "10.00"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/quotes")
                        .file(parteJson(corpoOrcamento("PDF-INVALIDO", sanepar, "PendingAcceptance", "1", "10.00", "10.00")))
                        .file(new MockMultipartFile("document", "documento.txt", MediaType.APPLICATION_PDF_VALUE, pdfDeTeste(80)))
                        .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/quotes")
                        .file(parteJson(corpoOrcamento("PDF-INVALIDO-2", sanepar, "PendingAcceptance", "1", "10.00", "10.00")))
                        .file(new MockMultipartFile("document", "documento.pdf", MediaType.APPLICATION_PDF_VALUE, "texto inválido".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", tokenAdministrador))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/quotes").header("Authorization", tokenTecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("SEM-PERFIL", sanepar, "PendingAcceptance", "1", "10.00", "10.00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOrcamento("SEM-AUTH", sanepar, "PendingAcceptance", "1", "10.00", "10.00")))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions executarBusca(String search) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/quotes")
                        .param("search", search)
                        .header("Authorization", tokenAdministrador);
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    private String corpoOrcamento(
            String code,
            Empresa empresa,
            String status,
            String sampleCount,
            String unitPrice,
            String totalPrice) {
        return """
                {
                  "code": "%s",
                  "companyId": "%s",
                  "name": "Proposta de ensaio",
                  "laboratoryName": "Laboratório de Materiais",
                  "description": "Caracterização mecânica de amostras",
                  "scope": "Ensaios de caracterização",
                  "externalContactName": " Contato do orçamento ",
                  "externalContactEmail": " NOVO.CONTATO@EXAMPLE.TEST ",
                  "status": "%s",
                  "totalPrice": %s,
                  "items": [{
                    "testType": "Resistência à tração",
                    "sampleCount": %s,
                    "standard": "ABNT NBR 1234",
                    "unitPrice": %s
                  }]
                }
                """.formatted(code, empresa.getId(), status, totalPrice, sampleCount, unitPrice);
    }

    private MockMultipartFile parteJson(String json) {
        return new MockMultipartFile(
                "quote", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] pdfDeTeste(int tamanho) {
        byte[] conteudo = new byte[tamanho];
        byte[] assinatura = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(assinatura, 0, conteudo, 0, assinatura.length);
        Arrays.fill(conteudo, assinatura.length, conteudo.length, (byte) 'x');
        return conteudo;
    }

    private Empresa criarEmpresa(String nome, String cnpj) {
        Empresa empresa = new Empresa();
        empresa.setLegalName(nome);
        empresa.setCnpj(cnpj);
        empresa.setAddress("Rua de teste, 1");
        empresa.setCity("Curitiba");
        empresa.setState("PR");
        empresa.setContactName("Contato");
        empresa.setContactPhone("41999990000");
        empresa.setContactEmail("contato@example.test");
        return empresas.saveAndFlush(empresa);
    }

    private Orcamento criarOrcamento(
            Empresa empresa,
            String code,
            String name,
            String description,
            StatusComercialOrcamento status,
            OffsetDateTime createdDate) {
        Orcamento orcamento = new Orcamento();
        orcamento.setEmpresa(empresa);
        orcamento.setCode(code);
        orcamento.setName(name);
        orcamento.setStatus(status);
        orcamento.setStage(status.codigo());
        orcamento.setDescription(description);
        orcamento.setTotalPrice(new BigDecimal("12500.00"));
        orcamento.setCreatedDate(createdDate);
        return orcamentos.saveAndFlush(orcamento);
    }

    private String token(PerfilUsuario perfil, String email) {
        Usuario usuario = new Usuario();
        usuario.setNome(perfil.name());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode("senha de teste"));
        usuario.setPerfil(perfil);
        usuario = usuarios.saveAndFlush(usuario);
        return "Bearer " + jwtService.generateToken(usuario);
    }
}
