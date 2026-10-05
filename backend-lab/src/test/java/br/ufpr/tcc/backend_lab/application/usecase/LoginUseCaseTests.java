package br.ufpr.tcc.backend_lab.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.LoginResponse;
import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class LoginUseCaseTests {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private LoginUseCase loginUseCase;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void cleanUsers() {
		usuarioRepository.deleteAll();
	}

	@Test
	void shouldAuthenticateActiveUserAndReturnToken() {
		Usuario usuario = usuario(PerfilUsuario.TECNICO, true);
		usuario.setSenhaHash(passwordEncoder.encode("senha"));
		usuarioRepository.save(usuario);

		LoginResponse response = loginUseCase.execute(
				new LoginRequest("tecnico@lab.com", "senha")
		);

		assertEquals("Bearer", response.tokenType());
		assertEquals(3, response.accessToken().split("\\.").length);
		assertEquals("TECNICO", response.user().role());
		assertEquals(3600L, response.expiresIn());
		var claims = jwtService.parseToken(response.accessToken()).getPayload();
		assertEquals("tcc-backend-lab", claims.getIssuer());
		assertTrue(claims.getAudience().contains("tcc-portal-api-internal"));
		assertEquals(usuario.getId(), ((Number) claims.get("userId")).longValue());
		assertEquals("TECNICO", claims.get("role", String.class));
		assertEquals("HS256", jwtService.parseToken(response.accessToken()).getHeader().getAlgorithm());
	}

	@Test
	void shouldRejectInactiveUser() {
		Usuario usuario = usuario(PerfilUsuario.ADMIN, false);
		usuario.setSenhaHash(passwordEncoder.encode("senha"));
		usuarioRepository.save(usuario);

		assertThrows(CredenciaisInvalidasException.class, () -> loginUseCase.execute(
				new LoginRequest("admin@lab.com", "senha")
		));
	}

	@Test
	void shouldRejectWrongPassword() {
		Usuario usuario = usuario(PerfilUsuario.ADMIN, true);
		usuario.setSenhaHash(passwordEncoder.encode("senha"));
		usuarioRepository.save(usuario);

		assertThrows(CredenciaisInvalidasException.class, () -> loginUseCase.execute(
				new LoginRequest("admin@lab.com", "errada")
		));
	}

	private Usuario usuario(PerfilUsuario perfil, boolean ativo) {
		Usuario usuario = new Usuario();
		usuario.setNome("Usuário de Teste");
		usuario.setEmail(perfil.name().toLowerCase() + "@lab.com");
		usuario.setSenhaHash("hash");
		usuario.setPerfil(perfil);
		usuario.setAtivo(ativo);
		return usuario;
	}
}
