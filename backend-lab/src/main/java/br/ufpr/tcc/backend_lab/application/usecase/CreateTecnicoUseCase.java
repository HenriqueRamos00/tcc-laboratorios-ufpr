package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.CreateTecnicoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoResponse;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.EmailUniqueConstraint;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateTecnicoUseCase {
	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public TecnicoResponse execute(CreateTecnicoRequest request) {
		String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
		if (usuarioRepository.existsByEmailIgnoreCase(email)) throw new EmailJaCadastradoException();

		Usuario tecnico = new Usuario();
		tecnico.setNome(request.name().trim());
		tecnico.setEmail(email);
		tecnico.setSenhaHash(passwordEncoder.encode(request.password()));
		tecnico.setPerfil(PerfilUsuario.TECNICO);
		tecnico.setUnidade(request.unit().trim());
		tecnico.setEspecialidade(request.specialty().trim());
		tecnico.setAtivo(request.status().toAtivo());
		try {
			return TecnicoResponse.fromEntity(usuarioRepository.saveAndFlush(tecnico));
		} catch (DataIntegrityViolationException exception) {
			if (EmailUniqueConstraint.isViolation(exception)) throw new EmailJaCadastradoException();
			throw exception;
		}
	}
}
