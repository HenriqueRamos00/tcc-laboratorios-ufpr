package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
class EquipamentoControllerTests {
	private static final String PATH = "/api/equipments";
	private String valid;
	private Empresa empresa;

	@Autowired private MockMvc mockMvc;
	@Autowired private UsuarioRepository usuarioRepository;
	@Autowired private EquipamentoRepository equipamentoRepository;
	@Autowired private EmpresaRepository empresaRepository;
	@Autowired private JwtService jwtService;

	@BeforeEach
	void limpaDados() {
		equipamentoRepository.deleteAll();
		usuarioRepository.deleteAll();
		empresaRepository.deleteAll();
		empresa = empresa("EMPRESA A");
		valid = """
				{"serialNumber":"SN-001","tag":"TAG-001","type":"TRANSFORMADOR","substation":"SE Norte","primaryVoltage":"13.8","companyId":"%s","riskLevel":"SAUDAVEL"}
				""".formatted(empresa.getId());
	}

	@Test
	void exigeTecnicoEmTodosOsEndpoints() throws Exception {
		Usuario tecnico = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "tecnico@example.test"));
		String token = bearer(tecnico);
		for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
			Equipamento existente = equipamentoRepository.saveAndFlush(equipamento("SN-" + method, "TAG-" + method, "EMPRESA B", TipoEquipamento.TANQUE, true));
			perform(method, null, existente.getId()).andExpect(status().isUnauthorized());
			perform(method, "Bearer " + jwtService.generateToken(
					usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin-" + method + "@example.test"))), existente.getId())
				.andExpect(status().isForbidden());
			perform(method, token, existente.getId()).andExpect(status().is2xxSuccessful());
		}
	}

	@ParameterizedTest
	@EnumSource(value = PerfilUsuario.class, mode = EnumSource.Mode.EXCLUDE, names = "TECNICO")
	void outrosPerfisSaoProibidosEmTodosOsEndpoints(PerfilUsuario perfil) throws Exception {
		Usuario usuario = usuarioRepository.saveAndFlush(usuario(perfil, perfil.name().toLowerCase() + "@example.test"));
		for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
			Equipamento existente = equipamentoRepository.saveAndFlush(equipamento("SN-" + method, "TAG-" + method, "EMPRESA B", TipoEquipamento.TANQUE, true));
			perform(method, bearer(usuario), existente.getId())
					.andExpect(perfil == PerfilUsuario.CLIENTE ? status().isUnauthorized() : status().isForbidden());
		}
	}

	@Test
	void criaEAtualizaEquipamentoComRoundTripCompleto() throws Exception {
		String token = bearer(usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "tecnico@example.test")));
		Empresa laboratorio = empresa("Empresa Fictícia");
		Empresa empresaDestino = empresa("EMPRESA B");
		String cadastro = """
				{"serialNumber":"  0000123  ","tag":"  TAG  ","type":"DISJUNTOR","substation":"SE Sul","primaryVoltage":"138.25","companyId":"%s","riskLevel":"ATENCAO"}
				""".formatted(laboratorio.getId());
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(cadastro))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", startsWith(PATH + "/")))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.serialNumber").value("0000123"))
				.andExpect(jsonPath("$.tag").value("TAG"))
				.andExpect(jsonPath("$.type").value("DISJUNTOR"))
				.andExpect(jsonPath("$.substation").value("SE Sul"))
				.andExpect(jsonPath("$.primaryVoltage").value("138.25"))
				.andExpect(jsonPath("$.owner").value("Empresa Fictícia"))
				.andExpect(jsonPath("$.companyId").value(laboratorio.getId().toString()))
				.andExpect(jsonPath("$.riskLevel").value("ATENCAO"));
		Equipamento criado = equipamentoRepository.findAll().get(0);
		String atualizacao = """
				{"serialNumber":" 0000123 ","tag":" TAG-NOVA ","type":"REATOR","substation":"SE Leste","primaryVoltage":"230","companyId":"%s","riskLevel":"CRITICO"}
				""".formatted(empresaDestino.getId());
		mockMvc.perform(put(PATH + "/{id}", criado.getId()).header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON).content(atualizacao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(criado.getId()))
				.andExpect(jsonPath("$.serialNumber").value("0000123"))
				.andExpect(jsonPath("$.tag").value("TAG-NOVA"))
				.andExpect(jsonPath("$.type").value("REATOR"))
				.andExpect(jsonPath("$.substation").value("SE Leste"))
				.andExpect(jsonPath("$.primaryVoltage").value("230"))
				.andExpect(jsonPath("$.owner").value("EMPRESA B"))
				.andExpect(jsonPath("$.companyId").value(empresaDestino.getId().toString()))
				.andExpect(jsonPath("$.riskLevel").value("CRITICO"));
	}

	@Test
	void permiteSerieETagDuplicadas() throws Exception {
		String token = bearer(usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "tecnico@example.test")));
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
					.content(valid))
				.andExpect(status().isCreated());
		}
		org.junit.jupiter.api.Assertions.assertEquals(2, equipamentoRepository.count());
	}

	@ParameterizedTest
	@EnumSource(TipoEquipamento.class)
	void aceitaTiposConhecidos(TipoEquipamento tipo) throws Exception {
		String token = tokenTecnico();
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(valid.replace("TRANSFORMADOR", tipo.name())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value(tipo.name()));
	}

	@ParameterizedTest
	@EnumSource(NivelRisco.class)
	void aceitaNiveisDeRiscoConhecidos(NivelRisco risco) throws Exception {
		String token = tokenTecnico();
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(valid.replace("SAUDAVEL", risco.name())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.riskLevel").value(risco.name()));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"0", "1", "\"0\"", "\"1\"", "\"OUTRO\""})
	void rejeitaEnumsInvalidosOuOrdinais(String valor) throws Exception {
		String token = tokenTecnico();
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(valid.replace("\"TRANSFORMADOR\"", valor)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(valid.replace("\"SAUDAVEL\"", valor)))
			.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"", " "})
	void rejeitaSerieVazia(String serial) throws Exception {
		String token = tokenTecnico();
		String request = valid.replace("SN-001", serial);
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isBadRequest());
	}

	@Test
	void validaTodosOsCamposObrigatorios() throws Exception {
		String token = tokenTecnico();
		JsonMapper mapper = JsonMapper.builder().build();
		for (String campo : new String[] {"serialNumber", "tag", "type", "substation", "primaryVoltage", "companyId", "riskLevel"}) {
			ObjectNode request = (ObjectNode) mapper.readTree(valid);
			request.remove(campo);
			String semCampo = mapper.writeValueAsString(request);
			mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(semCampo))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").exists());
		}
	}

	@Test
	void rejeitaTextoNuloOuEmBrancoSemCriarNemAlterarEquipamento() throws Exception {
		String token = tokenTecnico();
		Equipamento existente = equipamentoRepository.saveAndFlush(equipamento("ORIGINAL", "TAG-ORIGINAL", "EMPRESA B", TipoEquipamento.TANQUE, true));
		JsonMapper mapper = JsonMapper.builder().build();
		String[][] campos = {
			{"serialNumber", "Número de série"},
			{"tag", "TAG"},
			{"substation", "Subestação"},
			{"companyId", "Proprietário"}
		};
		for (String[] campo : campos) {
			for (boolean nulo : new boolean[] {true, false}) {
				ObjectNode request = (ObjectNode) mapper.readTree(valid);
				if (nulo) request.putNull(campo[0]); else request.put(campo[0], " ");
				String json = mapper.writeValueAsString(request);
				mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value(containsString(campo[1])));
				org.junit.jupiter.api.Assertions.assertEquals(1, equipamentoRepository.count());
			}
		}

		ObjectNode request = (ObjectNode) mapper.readTree(valid);
		request.put("serialNumber", "ALTERADO");
		request.put("tag", " ");
		mockMvc.perform(put(PATH + "/{id}", existente.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("TAG")));
		Equipamento persistido = equipamentoRepository.findById(existente.getId()).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals("ORIGINAL", persistido.getNumeroSerie());
		org.junit.jupiter.api.Assertions.assertEquals("TAG-ORIGINAL", persistido.getTag());
		org.junit.jupiter.api.Assertions.assertEquals(existente.getEmpresa().getId(), persistido.getEmpresa().getId());
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({"serialNumber,101", "tag,101", "substation,151"})
	void rejeitaTextoAcimaDoLimite(String campo, int tamanho) throws Exception {
		String request = switch (campo) {
			case "serialNumber" -> valid.replace("SN-001", "S".repeat(tamanho));
			case "tag" -> valid.replace("TAG-001", "T".repeat(tamanho));
			case "substation" -> valid.replace("SE Norte", "U".repeat(tamanho));
			default -> throw new IllegalArgumentException(campo);
		};
		mockMvc.perform(post(PATH).header("Authorization", tokenTecnico()).contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {
			"", "0", "-1", "0.000", "1.1234", "1234567", "1000000.1", "1,5", "1e3", "NaN", "abc"
	})
	void validaTensaoPrimaria(String tensao) throws Exception {
		String token = tokenTecnico();
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(valid.replace("13.8", tensao)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aceitaLimitesDosCamposETensao() throws Exception {
		String token = tokenTecnico();
		String request = """
				{"serialNumber":" %s ","tag":" %s ","type":"TRANSFORMADOR","substation":" %s ","primaryVoltage":"999999.999","companyId":"%s","riskLevel":"SAUDAVEL"}
				""".formatted("S".repeat(100), "T".repeat(100), "U".repeat(150), empresa.getId());
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.serialNumber").value("S".repeat(100)))
				.andExpect(jsonPath("$.tag").value("T".repeat(100)))
				.andExpect(jsonPath("$.substation").value("U".repeat(150)))
				.andExpect(jsonPath("$.owner").value("EMPRESA A"))
				.andExpect(jsonPath("$.primaryVoltage").value("999999.999"));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"0.001"})
	void aceitaMenorTensaoPositivaRepresentavel(String tensao) throws Exception {
		mockMvc.perform(post(PATH).header("Authorization", tokenTecnico()).contentType(MediaType.APPLICATION_JSON)
				.content(valid.replace("13.8", tensao)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.primaryVoltage").value(tensao));
	}

	@Test
	void pesquisaPorTrechoSemDiferenciarMaiusculasCombinaFiltrosEOrdena() throws Exception {
		String token = tokenTecnico();
		Equipamento z = equipamentoRepository.saveAndFlush(equipamento("ZZ-400", "BETA", "Empresa A Norte", TipoEquipamento.REATOR, true));
		Equipamento a = equipamentoRepository.saveAndFlush(equipamento("AA-100", "BETA", "EMPRESA A Sul", TipoEquipamento.REATOR, true));
		Equipamento a2 = equipamentoRepository.saveAndFlush(equipamento("AA-100", "BETA", "EMPRESA A Sul", TipoEquipamento.REATOR, true));
		equipamentoRepository.saveAndFlush(equipamento("BB-200", "BETA", "EMPRESA A", TipoEquipamento.DISJUNTOR, true));
		equipamentoRepository.saveAndFlush(equipamento("CC-300", "BETA", "EMPRESA A", TipoEquipamento.REATOR, false));

		mockMvc.perform(get(PATH).param("search", "empresa a").param("type", "REATOR")
				.header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(a.getId()))
			.andExpect(jsonPath("$[1].id").value(a2.getId()))
			.andExpect(jsonPath("$[2].id").value(z.getId()))
			.andExpect(jsonPath("$[3]").doesNotExist());
		mockMvc.perform(get(PATH).param("search", "aa-").header("Authorization", token))
			.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(a.getId()));
		Equipamento porTag = equipamento("OTHER", "TAG-Pesquisavel", "EMPRESA B", TipoEquipamento.TANQUE, true);
		equipamentoRepository.saveAndFlush(porTag);
		mockMvc.perform(get(PATH).param("search", "pesquisavel").header("Authorization", token))
			.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(porTag.getId()));
		mockMvc.perform(get(PATH).param("search", "beta").param("type", "REATOR").param("risk", "SAUDAVEL")
				.header("Authorization", token))
			.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(a.getId()));
		mockMvc.perform(get(PATH).param("search", "beta").param("type", "REATOR").param("risk", "CRITICO")
				.header("Authorization", token))
			.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void listaVaziaERejeitaEnumsInvalidosNosFiltros() throws Exception {
		String token = tokenTecnico();
		mockMvc.perform(get(PATH).header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(get(PATH).param("type", "INVALIDO").header("Authorization", token)).andExpect(status().isBadRequest());
		mockMvc.perform(get(PATH).param("risk", "INVALIDO").header("Authorization", token)).andExpect(status().isBadRequest());
	}

	@Test
	void putEmRegistroInexistenteOuInativoDa404SemAlteracao() throws Exception {
		String token = tokenTecnico();
		mockMvc.perform(put(PATH + "/{id}", 9999).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isNotFound());
		Equipamento inativo = equipamentoRepository.saveAndFlush(equipamento("INATIVO", "I", "EMPRESA B", TipoEquipamento.TANQUE, false));
		mockMvc.perform(put(PATH + "/{id}", inativo.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isNotFound());
		org.junit.jupiter.api.Assertions.assertEquals("INATIVO", equipamentoRepository.findById(inativo.getId()).orElseThrow().getNumeroSerie());
	}

	@Test
	void deleteEhIdempotenteParaInativoEDa404ParaDesconhecido() throws Exception {
		String token = tokenTecnico();
		Equipamento equipamento = equipamentoRepository.saveAndFlush(equipamento("SN", "TAG", "EMPRESA B", TipoEquipamento.TANQUE, true));
		mockMvc.perform(delete(PATH + "/{id}", equipamento.getId()).header("Authorization", token)).andExpect(status().isNoContent());
		mockMvc.perform(delete(PATH + "/{id}", equipamento.getId()).header("Authorization", token)).andExpect(status().isNoContent());
		Equipamento persistido = equipamentoRepository.findById(equipamento.getId()).orElseThrow();
		org.junit.jupiter.api.Assertions.assertFalse(persistido.isAtivo());
		org.junit.jupiter.api.Assertions.assertEquals("SN", persistido.getNumeroSerie());
		mockMvc.perform(delete(PATH + "/{id}", 9999).header("Authorization", token)).andExpect(status().isNotFound());
		mockMvc.perform(get(PATH).header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void listaEmpresasParaTecnicoSemLiberarCadastroNemDadosDeContato() throws Exception {
		Empresa inativa = empresa("EMPRESA B");
		inativa.setAtivo(false);
		empresaRepository.saveAndFlush(inativa);
		String token = tokenTecnico();
		mockMvc.perform(get(PATH + "/companies").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(empresa.getId().toString()))
				.andExpect(jsonPath("$[0].legalName").value("EMPRESA A"))
				.andExpect(jsonPath("$[0].cnpj").value(empresa.getCnpj()))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[1].status").value("INACTIVE"))
				.andExpect(jsonPath("$[0].address").doesNotExist())
				.andExpect(jsonPath("$[0].contactEmail").doesNotExist())
				.andExpect(jsonPath("$[0].contactPhone").doesNotExist());
		mockMvc.perform(get("/api/companies").header("Authorization", token)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/companies").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(PATH + "/companies")).andExpect(status().isUnauthorized());
		for (PerfilUsuario perfil : new PerfilUsuario[] {PerfilUsuario.ADMIN, PerfilUsuario.CLIENTE}) {
			String outroToken = bearer(usuarioRepository.saveAndFlush(usuario(perfil, perfil + "@example.test")));
			mockMvc.perform(get(PATH + "/companies").header("Authorization", outroToken))
					.andExpect(perfil == PerfilUsuario.CLIENTE ? status().isUnauthorized() : status().isForbidden());
		}
	}

	@Test
	void selecionaEmpresaPorIdMesmoComNomeIgualEBuscaPeloNomeAtual() throws Exception {
		Empresa homonima = empresa("EMPRESA A");
		String token = tokenTecnico();
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(dadosEmpresa(homonima.getId().toString())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.companyId").value(homonima.getId().toString()));
		Equipamento criado = equipamentoRepository.findAll().get(0);
		org.junit.jupiter.api.Assertions.assertEquals(homonima.getId(), criado.getEmpresa().getId());
		homonima.setLegalName("Empresa Renomeada");
		empresaRepository.saveAndFlush(homonima);
		mockMvc.perform(get(PATH).param("search", "renomeada").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(criado.getId()))
				.andExpect(jsonPath("$[0].owner").value("Empresa Renomeada"));
		mockMvc.perform(get(PATH).param("search", "empresa a").header("Authorization", token))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"", " ", "0", "-1", "abc", "1.5", "9223372036854775808", "99999999999999999999", "999999999"})
	void rejeitaEmpresaInvalidaOuInexistenteSemCriarNemAlterar(String id) throws Exception {
		String token = tokenTecnico();
		Equipamento existente = equipamentoRepository.saveAndFlush(equipamento("ORIGINAL", "TAG", "EMPRESA B", TipoEquipamento.TANQUE, true));
		String request = dadosEmpresa(id);
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put(PATH + "/{id}", existente.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isBadRequest());
		org.junit.jupiter.api.Assertions.assertEquals(1, equipamentoRepository.count());
		Equipamento persistido = equipamentoRepository.findById(existente.getId()).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals("ORIGINAL", persistido.getNumeroSerie());
		org.junit.jupiter.api.Assertions.assertEquals(existente.getEmpresa().getId(), persistido.getEmpresa().getId());
	}

	@Test
	void rejeitaNovosVinculosInativosMasPermiteManterOuTrocarOVinculoAtual() throws Exception {
		String token = tokenTecnico();
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(valid))
				.andExpect(status().isCreated());
		Equipamento existente = equipamentoRepository.findAll().get(0);
		empresa.setAtivo(false);
		empresaRepository.saveAndFlush(empresa);
		mockMvc.perform(post(PATH).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(valid))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put(PATH + "/{id}", existente.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(valid.replace("TAG-001", "EDITADO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tag").value("EDITADO"))
				.andExpect(jsonPath("$.companyId").value(empresa.getId().toString()));
		Empresa outraInativa = empresa("Outra inativa");
		outraInativa.setAtivo(false);
		empresaRepository.saveAndFlush(outraInativa);
		mockMvc.perform(put(PATH + "/{id}", existente.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(dadosEmpresa(outraInativa.getId().toString())))
				.andExpect(status().isBadRequest());
		Empresa ativa = empresa("Ativa");
		mockMvc.perform(put(PATH + "/{id}", existente.getId()).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(dadosEmpresa(ativa.getId().toString())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.companyId").value(ativa.getId().toString()))
				.andExpect(jsonPath("$.owner").value("Ativa"));
		org.junit.jupiter.api.Assertions.assertEquals(1, equipamentoRepository.count());
	}

	private String dadosEmpresa(String id) {
		JsonMapper mapper = JsonMapper.builder().build();
		ObjectNode request = (ObjectNode) mapper.readTree(valid);
		request.put("companyId", id);
		return mapper.writeValueAsString(request);
	}

	private org.springframework.test.web.servlet.ResultActions perform(String method, String token, Long id) throws Exception {
		var request = switch (method) {
			case "GET" -> get(PATH);
			case "POST" -> post(PATH).contentType(MediaType.APPLICATION_JSON).content(valid);
			case "PUT" -> put(PATH + "/" + id).contentType(MediaType.APPLICATION_JSON).content(valid);
			case "DELETE" -> delete(PATH + "/" + id);
			default -> throw new IllegalArgumentException(method);
		};
		if (token != null) request.header("Authorization", token);
		return mockMvc.perform(request);
	}

	private String tokenTecnico() {
		return bearer(usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "t" + System.nanoTime() + "@example.test")));
	}

	private String bearer(Usuario usuario) {
		return "Bearer " + jwtService.generateToken(usuario);
	}

	private Usuario usuario(PerfilUsuario perfil, String email) {
		Usuario usuario = new Usuario();
		usuario.setPerfil(perfil);
		usuario.setNome(perfil.name());
		usuario.setEmail(email);
		usuario.setSenhaHash("hash");
		usuario.setAtivo(true);
		return usuario;
	}

	private Empresa empresa(String nome) {
		Empresa nova = new Empresa();
		nova.setLegalName(nome);
		nova.setCnpj(String.format("%014d", empresaRepository.count() + 1));
		nova.setAddress("Endereço Teste, 1");
		nova.setCity("Cidade Teste");
		nova.setState("PR");
		nova.setContactName("Contato");
		nova.setContactPhone("0000000000");
		nova.setContactEmail("contato@example.test");
		return empresaRepository.saveAndFlush(nova);
	}

	private Equipamento equipamento(String serie, String tag, String proprietario, TipoEquipamento tipo, boolean ativo) {
		Equipamento equipamento = new Equipamento();
		equipamento.setNumeroSerie(serie);
		equipamento.setTag(tag);
		equipamento.setTipo(tipo);
		equipamento.setSubestacao("SE Teste");
		equipamento.setTensaoPrimaria(new BigDecimal("13.8"));
		equipamento.setEmpresa(empresa(proprietario));
		equipamento.setNivelRisco(NivelRisco.SAUDAVEL);
		equipamento.setAtivo(ativo);
		return equipamento;
	}
}
