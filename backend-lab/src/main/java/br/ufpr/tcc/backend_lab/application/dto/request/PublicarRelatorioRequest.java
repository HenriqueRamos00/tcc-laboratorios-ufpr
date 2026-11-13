package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PublicarRelatorioRequest(
        @NotNull(message = "Versão do relatório é obrigatória")
        @PositiveOrZero(message = "Versão do relatório deve ser zero ou positiva")
        Long version) { }
