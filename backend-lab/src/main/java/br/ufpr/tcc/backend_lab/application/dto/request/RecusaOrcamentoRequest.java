package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.Size;

public record RecusaOrcamentoRequest(
        @Size(max = 1000, message = "Justificativa deve ter no máximo 1000 caracteres")
                String justification) {
    public RecusaOrcamentoRequest {
        justification = justification == null ? null : justification.trim();
    }
}
