package br.ufpr.tcc.backend_auth.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record AtualizarServicoRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String code,
        @NotNull Boolean active) {
    public AtualizarServicoRequest {
        name = name == null ? null : name.trim();
        code = code == null ? null : code.trim().toLowerCase(Locale.ROOT);
    }
}
