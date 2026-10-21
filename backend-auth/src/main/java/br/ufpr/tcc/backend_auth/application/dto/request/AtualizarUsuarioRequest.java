package br.ufpr.tcc.backend_auth.application.dto.request;

import br.ufpr.tcc.backend_auth.application.validation.Normalizacao;
import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarUsuarioRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(min = 12, max = 72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Opcional: omita para preservar a senha. Máximo de 72 bytes UTF-8.") String password,
        @NotNull PerfilUsuario role,
        @NotNull Boolean active) {
    public AtualizarUsuarioRequest {
        name = name == null ? null : name.trim();
        email = Normalizacao.email(email);
    }

    @Override
    public String toString() {
        return "AtualizarUsuarioRequest[email=" + email + ", password=[REDACTED]]";
    }
}
