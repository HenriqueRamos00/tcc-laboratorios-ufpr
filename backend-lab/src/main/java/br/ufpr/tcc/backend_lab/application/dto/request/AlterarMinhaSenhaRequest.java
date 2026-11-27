package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarMinhaSenhaRequest(
        @NotBlank(message = "Senha atual é obrigatória")
                @Size(max = 72, message = "Senha atual deve ter no máximo 72 caracteres")
                String currentPassword,
        @NotBlank(message = "Nova senha é obrigatória")
                @Size(max = 72, message = "Nova senha deve ter no máximo 72 caracteres")
                String newPassword) {}
