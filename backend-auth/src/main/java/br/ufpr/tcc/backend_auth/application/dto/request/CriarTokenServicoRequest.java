package br.ufpr.tcc.backend_auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CriarTokenServicoRequest(
        @NotBlank @Size(max = 150) @Schema(example = "API C# em produção") String name,
        @Positive @Max(31536000)
        @Schema(example = "2592000", description = "Validade em segundos, até 365 dias. Omita para usar a configuração do serviço (30 dias por padrão).") Long expiresIn) {
    public CriarTokenServicoRequest {
        name = name == null ? null : name.trim();
    }
}
