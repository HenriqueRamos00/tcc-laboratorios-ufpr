package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.AuthenticatedUserResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.LoginResponse;
import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Transactional(readOnly = true)
	public LoginResponse execute(LoginRequest request) {
		Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim())
				.filter(Usuario::isAtivo)
				.orElseThrow(CredenciaisInvalidasException::new);

		if (!passwordEncoder.matches(request.password(), usuario.getSenhaHash())) {
			throw new CredenciaisInvalidasException();
		}

		return new LoginResponse(
				jwtService.generateToken(usuario),
				"Bearer",
				jwtService.getExpirationSeconds(),
				AuthenticatedUserResponse.fromEntity(usuario)
		);
	}
}
