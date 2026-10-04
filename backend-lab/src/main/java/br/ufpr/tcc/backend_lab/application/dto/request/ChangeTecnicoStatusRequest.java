package br.ufpr.tcc.backend_lab.application.dto.request;

import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeTecnicoStatusRequest(
		@NotNull(message = "Status é obrigatório")
		TecnicoStatus status
) {
}
