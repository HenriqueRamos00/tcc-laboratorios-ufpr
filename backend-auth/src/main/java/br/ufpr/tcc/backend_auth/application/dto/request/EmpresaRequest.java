package br.ufpr.tcc.backend_auth.application.dto.request;

import br.ufpr.tcc.backend_auth.application.validation.CnpjValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmpresaRequest(
        @NotBlank @Size(max = 150) @Schema(example = "Empresa de exemplo") String name,
        @NotBlank @Size(min = 14, max = 14)
        @Schema(example = "04252011000110", description = "CNPJ numérico ou alfanumérico válido; aceita máscara e normaliza para 14 caracteres em maiúsculas.") String cnpj) {
    public EmpresaRequest {
        name = name == null ? null : name.trim();
        cnpj = CnpjValidator.normalizar(cnpj);
    }
}
