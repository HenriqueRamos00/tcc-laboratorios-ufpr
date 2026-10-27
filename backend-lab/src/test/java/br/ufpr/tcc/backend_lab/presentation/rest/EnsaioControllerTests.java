package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.InstrumentoLaboratorial;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.InstrumentoLaboratorialRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
    @Autowired private OrcamentoRepository orcamentos;
    @Autowired private AmostraRepository amostras;
    @Autowired private InstrumentoLaboratorialRepository instrumentos;
    @Autowired private ExecucaoEnsaioRepository ensaios;
    @Autowired private JwtService jwtService;

    private Amostra amostra;
    private InstrumentoLaboratorial instrumento;
    private InstrumentoLaboratorial instrumentoSecundario;
    private Usuario tecnico;

    @BeforeEach
    void prepararReferencias() {
        ensaios.deleteAll();
        amostras.deleteAll();
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
        quote.setName("Proposta de teste");
        quote.setStatus(StatusComercialOrcamento.PENDING_ACCEPTANCE);
        quote.setStage("Negociação");
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
                .andExpect(status().isConflict());
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment.length()").value(2))
                .andExpect(jsonPath("$.measurements.length()").value(5))
                .andExpect(jsonPath("$.measurements[*].code").value(
                        contains("zero", "nd", "np", "texto", "gas")));
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
        mockMvc.perform(get(PATH + "/references/samples").header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quote.companyId").isString());
        mockMvc.perform(get(PATH + "/references/technicians").header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unit").value("DOLEO"));
        mockMvc.perform(get(PATH + "/measurement-templates/chromatography").header("Authorization", bearer(tecnico)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements.length()").value(11));
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

    private String corpo(String versionField, String measurements) {
        return "{\"sampleId\":\"" + amostra.getId() + "\",\"type\":\"Análise de teste\","
                + "\"analysisType\":\"chromatography\",\"method\":\"Método\",\"standard\":\"Norma\","
                + "\"scheduledDate\":\"2026-10-26\",\"responsibleId\":\"" + tecnico.getId() + "\","
                + "\"equipmentIds\":[\"" + instrumento.getId() + "\",\"" + instrumentoSecundario.getId()
                + "\"],\"analysisClassification\":null,"
                + "\"reportText\":\"Rascunho\", " + versionField + measurements + "}";
    }

    private String bearer(Usuario usuario) { return "Bearer " + jwtService.generateToken(usuario); }

    private String json(String body, String key) throws Exception {
        return JsonMapper.builder().build().readTree(body).get(key).asString();
    }
}
