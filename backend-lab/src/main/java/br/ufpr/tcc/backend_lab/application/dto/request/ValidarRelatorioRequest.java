package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ValidarRelatorioRequest(
        @NotNull(message = "Versão do relatório é obrigatória")
        @PositiveOrZero(message = "Versão do relatório deve ser zero ou positiva")
        Long version,
        @NotBlank(message = "Fingerprint do conteúdo revisado é obrigatório")
        @Pattern(regexp = "[0-9a-f]{64}", message = "Fingerprint do conteúdo revisado é inválido")
        String contentFingerprint) { }
