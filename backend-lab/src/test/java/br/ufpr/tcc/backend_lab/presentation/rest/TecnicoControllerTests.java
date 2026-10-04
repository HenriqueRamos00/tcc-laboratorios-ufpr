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
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
