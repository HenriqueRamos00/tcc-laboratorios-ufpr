package br.ufpr.tcc.backend_lab.application.dto.request;

import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;

public record CreateClienteRequest(
        @NotBlank(message = "Nome é obrigatório")
                @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
                String name,
        @NotBlank(message = "E-mail é obrigatório")
                @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
                @Email(message = "E-mail inválido")
                String email,
        @NotBlank(message = "Telefone é obrigatório")
                @Size(max = 30, message = "Telefone deve ter no máximo 30 caracteres")
                String phone,
        @NotBlank(message = "Cargo é obrigatório")
                @Size(max = 120, message = "Cargo deve ter no máximo 120 caracteres")
                String position,
        @NotNull(message = "Status é obrigatório") CadastroStatus status,
        @NotEmpty(message = "Selecione ao menos uma empresa")
                List<
                        @NotBlank(message = "Identificador da empresa é obrigatório")
                        @Pattern(
                                regexp = "[1-9][0-9]{0,18}",
                                message =
                                        "Identificador da empresa deve ser um número inteiro positivo")
                        String>
                        companyIds,
        @NotBlank(message = "Senha inicial é obrigatória")
                @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
                String password) {
    public CreateClienteRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = phone == null ? null : phone.trim();
        position = position == null ? null : position.trim();
    }
}
