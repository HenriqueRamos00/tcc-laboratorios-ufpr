package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TecnicoControllerTests {
	@Autowired private MockMvc mockMvc;
	@Autowired private UsuarioRepository usuarioRepository;
	@Autowired private JwtService jwtService;
	@Value("${security.jwt.secret}") private String jwtSecret;

	@BeforeEach
	void limpaUsuarios() {
		usuarioRepository.deleteAll();
	}

	@Test
	void exigeAdministradorNosEndpointsDeTecnicos() throws Exception {
		Usuario admin = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));
		Usuario tecnico = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "tecnico@lab.com", true));

		mockMvc.perform(get("/api/technicians"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/technicians").header("Authorization", bearer(jwtService.generateToken(tecnico))))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/technicians").header("Authorization", bearer(jwtService.generateToken(admin))))
				.andExpect(status().isOk());
	}

	@Test
	void rejeitaTokenExpirado() throws Exception {
		Usuario admin = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));
		String token = tokenPara(admin)
				.expiration(Date.from(Instant.now().minusSeconds(120)))
				.signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();

		mockMvc.perform(get("/api/technicians").header("Authorization", bearer(token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaTokenAssinadoComOutraChave() throws Exception {
		Usuario admin = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));
		String outraChave = "outra-chave-com-pelo-menos-32-caracteres-para-testes";
		String token = tokenPara(admin)
				.signWith(Keys.hmacShaKeyFor(outraChave.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();

		mockMvc.perform(get("/api/technicians").header("Authorization", bearer(token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaTokenComAlgoritmoDiferenteDeHs256() throws Exception {
		Usuario admin = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));
		String token = tokenPara(admin)
				.signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS384)
				.compact();

		mockMvc.perform(get("/api/technicians").header("Authorization", bearer(token)))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "\"OUTRO\""})
	void rejeitaStatusForaDoContratoSemAlterarTecnico(String valorJson) throws Exception {
		Usuario admin = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));
		Usuario tecnico = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "tecnico@lab.com", false));
		String token = bearer(jwtService.generateToken(admin));

		mockMvc.perform(patch("/api/technicians/{id}/status", tecnico.getId())
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":" + valorJson + "}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/technicians?status=INACTIVE").header("Authorization", token))
				.andExpect(jsonPath("$[0].id").value(tecnico.getId()))
				.andExpect(jsonPath("$[0].status").value("INACTIVE"));
	}

	@Test
	void validaCadastroERejeitaEmailDuplicadoSemDiferenciarMaiusculas() throws Exception {
		Usuario admin = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));
		String token = bearer(jwtService.generateToken(admin));
		String request = """
				{"name":"  Maria Santos ","email":" Maria@Example.com ","unit":" DOLEO ","specialty":" Óleos ","status":"ACTIVE","password":"senha"}
				""";

		mockMvc.perform(post("/api/technicians").header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Maria Santos"))
				.andExpect(jsonPath("$.email").value("maria@example.com"))
				.andExpect(jsonPath("$.senhaHash").doesNotExist());

		mockMvc.perform(post("/api/technicians").header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON)
					.content(request.replace("Maria@Example.com", "MARIA@example.com")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").exists());

		mockMvc.perform(post("/api/technicians").header("Authorization", token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void bloqueiaTokenDeContaDesativada() throws Exception {
		Usuario tecnico = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "tecnico@lab.com", true));
		String token = bearer(jwtService.generateToken(tecnico));

		tecnico.setAtivo(false);
		usuarioRepository.saveAndFlush(tecnico);

		mockMvc.perform(get("/api/health/private-probe").header("Authorization", token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void naoAssociaTokenAntigoAOutraContaComMesmoEmail() throws Exception {
		Usuario administradorAnterior = usuarioRepository.saveAndFlush(
				usuario(PerfilUsuario.ADMIN, "admin@lab.com", true)
		);
		String tokenAnterior = bearer(jwtService.generateToken(administradorAnterior));

		administradorAnterior.setEmail("admin-renomeado@lab.com");
		administradorAnterior.setAtivo(false);
		usuarioRepository.saveAndFlush(administradorAnterior);
		usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));

		mockMvc.perform(get("/api/technicians").header("Authorization", tokenAnterior))
				.andExpect(status().isUnauthorized());
	}

	private JwtBuilder tokenPara(Usuario usuario) {
		Instant agora = Instant.now();
		return Jwts.builder()
				.subject(usuario.getEmail())
				.claim("userId", usuario.getId())
				.claim("role", usuario.getPerfil().name())
				.issuedAt(Date.from(agora))
				.expiration(Date.from(agora.plusSeconds(300)));
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private Usuario usuario(PerfilUsuario perfil, String email, boolean ativo) {
		Usuario usuario = new Usuario();
		usuario.setPerfil(perfil);
		usuario.setNome(perfil.name());
		usuario.setEmail(email);
		usuario.setSenhaHash("hash");
		usuario.setAtivo(ativo);
		return usuario;
	}
}
