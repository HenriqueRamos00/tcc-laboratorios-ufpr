package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "E-mail é obrigatório")
                @Email(message = "E-mail inválido")
                @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
                String email,
        @NotBlank(message = "Senha é obrigatória")
                @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
                String password) {
    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
