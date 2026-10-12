package br.ufpr.tcc.backend_lab.application.dto.request;

import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CreateEmpresaRequest(
        @NotBlank(message = "Razão social é obrigatória")
                @Size(max = 200, message = "Razão social deve ter no máximo 200 caracteres")
                String legalName,
        @NotBlank(message = "CNPJ é obrigatório")
                @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
                String cnpj,
        @NotBlank(message = "Endereço é obrigatório")
                @Size(max = 255, message = "Endereço deve ter no máximo 255 caracteres")
                String address,
        @NotBlank(message = "Cidade é obrigatória")
                @Size(max = 120, message = "Cidade deve ter no máximo 120 caracteres")
                String city,
        @NotBlank(message = "UF é obrigatória")
                @Size(max = 2, message = "UF deve ter 2 caracteres")
                String state,
        @NotBlank(message = "Nome do contato é obrigatório")
                @Size(max = 150, message = "Nome do contato deve ter no máximo 150 caracteres")
                String contactName,
        @NotBlank(message = "Telefone de contato é obrigatório")
                @Size(max = 30, message = "Telefone de contato deve ter no máximo 30 caracteres")
                String contactPhone,
        @NotBlank(message = "E-mail de contato é obrigatório")
                @Size(max = 254, message = "E-mail de contato deve ter no máximo 254 caracteres")
                @Email(message = "E-mail de contato inválido")
                String contactEmail,
        @NotNull(message = "Status é obrigatório") CadastroStatus status) {
    public CreateEmpresaRequest {
        legalName = legalName == null ? null : legalName.trim();
        cnpj = cnpj == null ? null : cnpj.trim();
        address = address == null ? null : address.trim();
        city = city == null ? null : city.trim();
        state = state == null ? null : state.trim().toUpperCase(Locale.ROOT);
        contactName = contactName == null ? null : contactName.trim();
        contactPhone = contactPhone == null ? null : contactPhone.trim();
        contactEmail =
                contactEmail == null
                        ? null
                        : contactEmail.trim().toLowerCase(Locale.ROOT);
    }
}
