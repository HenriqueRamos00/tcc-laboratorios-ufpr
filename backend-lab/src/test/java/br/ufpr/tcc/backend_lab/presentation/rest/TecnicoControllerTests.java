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
	@Value("${security.jwt.issuer}") private String jwtIssuer;
	@Value("${security.jwt.audience}") private String jwtAudience;

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

	@ParameterizedTest
	@ValueSource(strings = {"audiencia", "emissor", "expirado", "emissaoFutura", "semEmissao", "semExpiracao"})
	void rejeitaTokenAssinadoComContratoInvalido(String caso) throws Exception {
		Usuario admin = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true));
		Instant agora = Instant.now();
		JwtBuilder builder = Jwts.builder()
				.subject(admin.getEmail())
				.claim("userId", admin.getId())
				.claim("role", "ADMIN")
				.issuer(jwtIssuer)
				.audience().add(jwtAudience).and()
				.issuedAt(Date.from(agora))
				.expiration(Date.from(agora.plusSeconds(300)));
		switch (caso) {
			case "audiencia" -> builder.audience().clear().add("outra-api").and();
			case "emissor" -> builder.issuer("outro-emissor");
			case "expirado" -> builder.expiration(Date.from(agora.minusSeconds(120)));
			case "emissaoFutura" -> builder.issuedAt(Date.from(agora.plusSeconds(120)));
			case "semEmissao" -> builder.issuedAt(null);
			case "semExpiracao" -> builder.expiration(null);
			default -> throw new IllegalArgumentException(caso);
		}
		String token = builder.signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();

		mockMvc.perform(get("/api/technicians").header("Authorization", bearer(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Não autenticado"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "1", "\"0\"", "\"1\"", "\"OUTRO\""})
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
	void rejeitaTokensAntigosEBloqueiaTokenDeContaInativa() throws Exception {
		Usuario tecnico = usuarioRepository.saveAndFlush(usuario(PerfilUsuario.TECNICO, "tecnico@lab.com", true));
		String currentToken = bearer(jwtService.generateToken(tecnico));
		String legacyToken = bearer(Jwts.builder()
				.subject(tecnico.getEmail())
				.claim("role", "TECNICO")
				.expiration(Date.from(Instant.now().plusSeconds(300)))
				.signWith(Keys.hmacShaKeyFor("test-secret-with-at-least-32-characters-long".getBytes(StandardCharsets.UTF_8)))
				.compact());

		mockMvc.perform(get("/api/health/private-probe").header("Authorization", legacyToken))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/api/technicians/{id}/status", tecnico.getId())
					.header("Authorization", bearer(jwtService.generateToken(usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "admin@lab.com", true)))))
					.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/health/private-probe").header("Authorization", currentToken))
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
