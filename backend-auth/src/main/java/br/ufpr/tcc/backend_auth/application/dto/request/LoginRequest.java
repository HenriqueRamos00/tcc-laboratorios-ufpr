package br.ufpr.tcc.backend_auth.application.dto.request;

import br.ufpr.tcc.backend_auth.application.validation.Normalizacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
        @Schema(example = "cliente@example.com", format = "email") String email,
        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Senha da conta, preservando espaços; máximo de 72 bytes UTF-8.",
                example = "Uma senha forte 123!") String password) {
    public LoginRequest {
        email = Normalizacao.email(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=[REDACTED]]";
    }
}
