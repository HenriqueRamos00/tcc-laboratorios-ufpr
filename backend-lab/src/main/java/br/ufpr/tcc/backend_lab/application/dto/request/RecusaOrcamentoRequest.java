package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.Size;

public record RecusaOrcamentoRequest(
        @Size(max = 200, message = "Justificativa deve ter no máximo 200 caracteres")
                String justification) {
    public RecusaOrcamentoRequest {
        justification = justification == null ? null : justification.trim();
    }
}
