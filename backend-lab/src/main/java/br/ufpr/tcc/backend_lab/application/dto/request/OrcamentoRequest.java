package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public record OrcamentoRequest(
        @NotBlank(message = "Código da proposta é obrigatório")
                @Size(max = 80, message = "Código da proposta deve ter no máximo 80 caracteres")
                String code,
        @NotBlank(message = "Empresa é obrigatória")
                @Pattern(
                        regexp = "[1-9][0-9]{0,18}",
                        message = "Identificador da empresa deve ser um número inteiro positivo")
                String companyId,
        @NotBlank(message = "Nome do orçamento é obrigatório")
                @Size(max = 200, message = "Nome do orçamento deve ter no máximo 200 caracteres")
                String name,
        @NotBlank(message = "Nome do laboratório é obrigatório")
                @Size(max = 120, message = "Nome do laboratório deve ter no máximo 120 caracteres")
                String laboratoryName,
        @NotBlank(message = "Descrição é obrigatória")
                @Size(max = 2000, message = "Descrição deve ter no máximo 2000 caracteres")
                String description,
        @Size(max = 2000, message = "Escopo deve ter no máximo 2000 caracteres") String scope,
        @NotBlank(message = "Nome do contato externo é obrigatório")
                @Size(max = 150, message = "Nome do contato deve ter no máximo 150 caracteres")
                String externalContactName,
        @NotBlank(message = "E-mail do contato externo é obrigatório")
                @Email(message = "E-mail do contato externo inválido")
                @Size(max = 254, message = "E-mail do contato deve ter no máximo 254 caracteres")
                String externalContactEmail,
        @NotBlank(message = "Status é obrigatório")
                @Size(max = 80, message = "Status deve ter no máximo 80 caracteres")
                String status,
        @NotNull(message = "Valor total é obrigatório")
                @DecimalMin(value = "0", message = "Valor total não pode ser negativo")
                @Digits(
                        integer = 16,
                        fraction = 2,
                        message = "Valor total deve ter até 16 dígitos inteiros e 2 decimais")
                BigDecimal totalPrice,
        @NotEmpty(message = "Informe ao menos um item de serviço")
                List<@NotNull(message = "Os itens do orçamento não podem ser nulos") @Valid OrcamentoItemRequest> items) {
    public OrcamentoRequest {
        code = code == null ? null : code.trim();
        companyId = companyId == null ? null : companyId.trim();
        name = name == null ? null : name.trim();
        laboratoryName = laboratoryName == null ? null : laboratoryName.trim();
        description = description == null ? null : description.trim();
        scope = scope == null ? null : scope.trim();
        externalContactName = externalContactName == null ? null : externalContactName.trim();
        externalContactEmail =
                externalContactEmail == null
                        ? null
                        : externalContactEmail.trim().toLowerCase(Locale.ROOT);
    }
}
