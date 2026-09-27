package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "O e-mail é obrigatório")
		@Email(message = "O e-mail deve ser válido")
		String email,
		@NotBlank(message = "A senha é obrigatória")
		String password
) {
}
