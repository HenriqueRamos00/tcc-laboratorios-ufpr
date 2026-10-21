package br.ufpr.tcc.backend_auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CriarServicoRequest(
        @NotBlank @Size(max = 150) @Schema(example = "Portal Web") String name,
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
        @Schema(example = "portal-web", description = "Código único: letras minúsculas, números e hífens.") String code) {
    public CriarServicoRequest {
        name = name == null ? null : name.trim();
        code = code == null ? null : code.trim().toLowerCase(Locale.ROOT);
    }
}
