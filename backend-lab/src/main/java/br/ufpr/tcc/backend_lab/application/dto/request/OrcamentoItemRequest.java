package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrcamentoItemRequest(
        @NotBlank(message = "Tipo de ensaio é obrigatório")
                @Size(max = 200, message = "Tipo de ensaio deve ter no máximo 200 caracteres")
                String testType,
        @NotNull(message = "Quantidade de amostras é obrigatória")
                @DecimalMin(value = "1", message = "Quantidade de amostras deve ser maior que zero")
                @DecimalMax(
                        value = "2147483647",
                        message = "Quantidade de amostras excede o limite permitido")
                @Digits(
                        integer = 10,
                        fraction = 0,
                        message = "Quantidade de amostras deve ser um número inteiro")
                BigDecimal sampleCount,
        @Size(max = 255, message = "Norma deve ter no máximo 255 caracteres") String standard,
        @NotNull(message = "Preço unitário é obrigatório")
                @DecimalMin(value = "0", message = "Preço unitário não pode ser negativo")
                @Digits(
                        integer = 16,
                        fraction = 2,
                        message = "Preço unitário deve ter até 16 dígitos inteiros e 2 decimais")
                BigDecimal unitPrice) {
    public OrcamentoItemRequest {
        testType = testType == null ? null : testType.trim();
        standard = standard == null ? null : standard.trim();
    }
}
