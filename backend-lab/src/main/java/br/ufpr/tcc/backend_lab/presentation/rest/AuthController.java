package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.PasswordRecoveryRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.PasswordResetRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.LoginResponse;
import br.ufpr.tcc.backend_lab.application.usecase.LoginUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.RequestPasswordRecoveryUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ResetPasswordUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final LoginUseCase loginUseCase;
	private final RequestPasswordRecoveryUseCase requestPasswordRecoveryUseCase;
	private final ResetPasswordUseCase resetPasswordUseCase;

	@PostMapping("/internal/login")
	public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
		return ResponseEntity.ok(loginUseCase.execute(request));
	}

	@PostMapping("/password/forgot")
	public ResponseEntity<Void> requestPasswordRecovery(
			@RequestBody @Valid PasswordRecoveryRequest request) {
		requestPasswordRecoveryUseCase.execute(request.email());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password/reset")
	public ResponseEntity<Void> resetPassword(@RequestBody @Valid PasswordResetRequest request) {
		if (!resetPasswordUseCase.execute(request.token(), request.password())) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
