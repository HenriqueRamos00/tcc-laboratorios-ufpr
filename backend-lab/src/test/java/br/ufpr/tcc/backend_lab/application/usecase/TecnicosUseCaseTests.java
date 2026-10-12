package br.ufpr.tcc.backend_lab.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufpr.tcc.backend_lab.application.dto.request.CreateTecnicoRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.UpdateTecnicoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoStatus;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.exception.TecnicoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class TecnicosUseCaseTests {
	@Autowired private UsuarioRepository usuarioRepository;
	@Autowired private ListTecnicosUseCase listTecnicosUseCase;
	@Autowired private CreateTecnicoUseCase createTecnicoUseCase;
	@Autowired private UpdateTecnicoUseCase updateTecnicoUseCase;
	@Autowired private ChangeTecnicoStatusUseCase changeTecnicoStatusUseCase;
	@Autowired private PasswordEncoder passwordEncoder;

	@BeforeEach
	void limpaUsuarios() {
		usuarioRepository.deleteAll();
	}

	@Test
	void pesquisaSomenteTecnicosPorNomeEmailOuUnidadeEStatus() {
		usuarioRepository.save(tecnico("Técnico A", "tecnico-a@example.test", "UNIDADE_A", true));
		usuarioRepository.save(tecnico("Técnico B", "tecnico-b@example.test", "UNIDADE_B", false));
		usuarioRepository.save(usuario(PerfilUsuario.ADMIN, "Administrador Teste", "admin@example.test", "UNIDADE_A", true));

		var porUnidade = listTecnicosUseCase.execute("unidade_a", null);
		assertEquals(1, porUnidade.size());
		assertEquals("Técnico A", porUnidade.getFirst().name());
		assertEquals(1, listTecnicosUseCase.execute("técnico a", null).size());

		var ativos = listTecnicosUseCase.execute("tecnico-a@EXAMPLE.TEST", TecnicoStatus.ACTIVE);
		assertEquals(1, ativos.size());
		assertEquals("ACTIVE", ativos.getFirst().status().name());
	}

	@Test
	void criaTecnicoComDadosNormalizadosESenhaComBcrypt() {
		var response = createTecnicoUseCase.execute(new CreateTecnicoRequest(
				"  Técnico C ", " TECNICO-C@EXAMPLE.TEST ", " UNIDADE_A ", " Análise de Óleos ",
				TecnicoStatus.ACTIVE, "senha inicial"
		));

		Usuario saved = usuarioRepository.findById(response.id()).orElseThrow();
		assertEquals("Técnico C", response.name());
		assertEquals("tecnico-c@example.test", response.email());
		assertEquals("UNIDADE_A", response.unit());
		assertEquals("TECNICO", saved.getPerfil().name());
		assertTrue(passwordEncoder.matches("senha inicial", saved.getSenhaHash()));
		assertNotEquals("senha inicial", saved.getSenhaHash());
	}

	@Test
	void preservaSenhaNaEdicaoESubstituiSeInformada() {
		Usuario original = tecnico("Técnico C", "tecnico-c@example.test", "UNIDADE_A", true);
		original.setSenhaHash(passwordEncoder.encode("senha antiga"));
		original = usuarioRepository.saveAndFlush(original);
		String hashAnterior = original.getSenhaHash();

		updateTecnicoUseCase.execute(original.getId(), new UpdateTecnicoRequest(
				"Técnico C", "tecnico-c@example.test", "UNIDADE_B", "Compósitos", TecnicoStatus.ACTIVE, ""
		));
		Usuario editado = usuarioRepository.findById(original.getId()).orElseThrow();
		assertEquals(hashAnterior, editado.getSenhaHash());
		assertEquals("UNIDADE_B", editado.getUnidade());

		updateTecnicoUseCase.execute(original.getId(), new UpdateTecnicoRequest(
				"Técnico C", "tecnico-c@example.test", "UNIDADE_B", "Compósitos", TecnicoStatus.ACTIVE, "senha nova"
		));
		assertTrue(passwordEncoder.matches("senha nova", usuarioRepository.findById(original.getId()).orElseThrow().getSenhaHash()));
	}

	@Test
	void rejeitaEmailDuplicadoSemDiferenciarMaiusculasETrataIdDeAdministradorComoNaoEncontrado() {
		usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "Admin", "admin@example.test", null, true));
		assertThrows(EmailJaCadastradoException.class, () -> createTecnicoUseCase.execute(new CreateTecnicoRequest(
				"Pessoa", "ADMIN@EXAMPLE.TEST", "UNIDADE_A", "Química", TecnicoStatus.ACTIVE, "senha"
		)));

		Usuario admin = usuarioRepository.findByEmailIgnoreCase("admin@example.test").orElseThrow();
		assertThrows(TecnicoNaoEncontradoException.class, () -> updateTecnicoUseCase.execute(admin.getId(), new UpdateTecnicoRequest(
				"Admin", "admin@example.test", "UNIDADE_A", "Química", TecnicoStatus.ACTIVE, null
		)));
	}

	@Test
	void alteraStatusDoTecnico() {
		Usuario tecnico = usuarioRepository.saveAndFlush(tecnico("Técnico D", "tecnico-d@example.test", "UNIDADE_A", true));
		var response = changeTecnicoStatusUseCase.execute(tecnico.getId(), TecnicoStatus.INACTIVE);

		assertEquals(TecnicoStatus.INACTIVE, response.status());
		assertFalse(usuarioRepository.findById(tecnico.getId()).orElseThrow().isAtivo());
	}

	private Usuario tecnico(String nome, String email, String unidade, boolean ativo) {
		return usuario(PerfilUsuario.TECNICO, nome, email, unidade, ativo);
	}

	private Usuario usuario(PerfilUsuario perfil, String nome, String email, String unidade, boolean ativo) {
		Usuario usuario = new Usuario();
		usuario.setPerfil(perfil);
		usuario.setNome(nome);
		usuario.setEmail(email);
		usuario.setSenhaHash(passwordEncoder.encode("senha"));
		usuario.setUnidade(unidade);
		usuario.setEspecialidade("Química");
		usuario.setAtivo(ativo);
		return usuario;
	}
}
