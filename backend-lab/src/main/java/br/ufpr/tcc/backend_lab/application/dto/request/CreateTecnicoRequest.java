package br.ufpr.tcc.backend_lab.application.dto.request;

import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTecnicoRequest(
		@NotBlank(message = "Nome é obrigatório")
		@Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
		String name,

		@NotBlank(message = "E-mail é obrigatório")
		@Email(message = "E-mail inválido")
		@Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
		String email,

		@NotBlank(message = "Unidade é obrigatória")
		@Size(max = 120, message = "Unidade deve ter no máximo 120 caracteres")
		String unit,

		@NotBlank(message = "Especialidade é obrigatória")
		@Size(max = 160, message = "Especialidade deve ter no máximo 160 caracteres")
		String specialty,

		@NotNull(message = "Status é obrigatório")
		TecnicoStatus status,

		@NotBlank(message = "Senha é obrigatória no cadastro")
		@Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
		String password
) {
	public CreateTecnicoRequest {
		name = name == null ? null : name.trim();
		email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
		unit = unit == null ? null : unit.trim();
		specialty = specialty == null ? null : specialty.trim();
	}
}
