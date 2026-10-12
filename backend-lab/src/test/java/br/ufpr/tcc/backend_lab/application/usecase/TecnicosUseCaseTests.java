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
		usuarioRepository.save(tecnico("Ana Lima", "ana@lab.com", "DOLEO", true));
		usuarioRepository.save(tecnico("Bruno Reis", "bruno@lab.com", "DIMAT", false));
		usuarioRepository.save(usuario(PerfilUsuario.ADMIN, "Ana Admin", "admin@lab.com", "DOLEO", true));

		var porUnidade = listTecnicosUseCase.execute("doleo", null);
		assertEquals(1, porUnidade.size());
		assertEquals("Ana Lima", porUnidade.getFirst().name());
		assertEquals(1, listTecnicosUseCase.execute("ana lima", null).size());

		var ativos = listTecnicosUseCase.execute("ana@LAB.COM", TecnicoStatus.ACTIVE);
		assertEquals(1, ativos.size());
		assertEquals("ACTIVE", ativos.getFirst().status().name());
	}

	@Test
	void criaTecnicoComDadosNormalizadosESenhaComBcrypt() {
		var response = createTecnicoUseCase.execute(new CreateTecnicoRequest(
				"  Maria Santos ", " MARIA@EXAMPLE.COM ", " DOLEO ", " Análise de Óleos ",
				TecnicoStatus.ACTIVE, "senha inicial"
		));

		Usuario saved = usuarioRepository.findById(response.id()).orElseThrow();
		assertEquals("Maria Santos", response.name());
		assertEquals("maria@example.com", response.email());
		assertEquals("DOLEO", response.unit());
		assertEquals("TECNICO", saved.getPerfil().name());
		assertTrue(passwordEncoder.matches("senha inicial", saved.getSenhaHash()));
		assertNotEquals("senha inicial", saved.getSenhaHash());
	}

	@Test
	void preservaSenhaNaEdicaoESubstituiSeInformada() {
		Usuario original = tecnico("Maria Santos", "maria@lab.com", "DOLEO", true);
		original.setSenhaHash(passwordEncoder.encode("senha antiga"));
		original = usuarioRepository.saveAndFlush(original);
		String hashAnterior = original.getSenhaHash();

		updateTecnicoUseCase.execute(original.getId(), new UpdateTecnicoRequest(
				"Maria Santos", "maria@lab.com", "DIMAT", "Compósitos", TecnicoStatus.ACTIVE, ""
		));
		Usuario editado = usuarioRepository.findById(original.getId()).orElseThrow();
		assertEquals(hashAnterior, editado.getSenhaHash());
		assertEquals("DIMAT", editado.getUnidade());

		updateTecnicoUseCase.execute(original.getId(), new UpdateTecnicoRequest(
				"Maria Santos", "maria@lab.com", "DIMAT", "Compósitos", TecnicoStatus.ACTIVE, "senha nova"
		));
		assertTrue(passwordEncoder.matches("senha nova", usuarioRepository.findById(original.getId()).orElseThrow().getSenhaHash()));
	}

	@Test
	void rejeitaEmailDuplicadoSemDiferenciarMaiusculasETrataIdDeAdministradorComoNaoEncontrado() {
		usuarioRepository.saveAndFlush(usuario(PerfilUsuario.ADMIN, "Admin", "admin@lab.com", null, true));
		assertThrows(EmailJaCadastradoException.class, () -> createTecnicoUseCase.execute(new CreateTecnicoRequest(
				"Pessoa", "ADMIN@LAB.COM", "DOLEO", "Química", TecnicoStatus.ACTIVE, "senha"
		)));

		Usuario admin = usuarioRepository.findByEmailIgnoreCase("admin@lab.com").orElseThrow();
		assertThrows(TecnicoNaoEncontradoException.class, () -> updateTecnicoUseCase.execute(admin.getId(), new UpdateTecnicoRequest(
				"Admin", "admin@lab.com", "DOLEO", "Química", TecnicoStatus.ACTIVE, null
		)));
	}

	@Test
	void alteraStatusDoTecnico() {
		Usuario tecnico = usuarioRepository.saveAndFlush(tecnico("Rui", "rui@lab.com", "DOLEO", true));
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
