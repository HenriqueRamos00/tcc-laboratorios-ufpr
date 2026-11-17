package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "Token é obrigatório")
                @Size(max = 128, message = "Token inválido")
                String token,
        @NotBlank(message = "Senha é obrigatória")
                @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
                String password) {

    @Override
    public String toString() {
        return "PasswordResetRequest[token=[OMITIDO], password=[OMITIDA]]";
    }
}
