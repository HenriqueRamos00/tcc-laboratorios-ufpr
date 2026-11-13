package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TermoAceiteRequest(
        @NotNull(message = "Dados de cobrança são obrigatórios") @Valid Cobranca billing,
        @NotNull(message = "Dados da aprovação são obrigatórios") @Valid Aprovacao approval) {

    public record Cobranca(
            @NotBlank(message = "Empresa é obrigatória")
                    @Size(max = 200, message = "Empresa deve ter no máximo 200 caracteres")
                    String company,
            @NotBlank(message = "CNPJ é obrigatório")
                    @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
                    String cnpj,
            @Size(max = 30, message = "Inscrição estadual deve ter no máximo 30 caracteres")
                    String stateRegistration,
            @NotBlank(message = "Endereço é obrigatório")
                    @Size(max = 255, message = "Endereço deve ter no máximo 255 caracteres")
                    String street,
            @NotBlank(message = "Cidade é obrigatória")
                    @Size(max = 120, message = "Cidade deve ter no máximo 120 caracteres")
                    String city,
            @NotBlank(message = "Estado é obrigatório")
                    @Size(max = 60, message = "Estado deve ter no máximo 60 caracteres")
                    String state,
            @Size(max = 120, message = "Bairro deve ter no máximo 120 caracteres") String district,
            @NotBlank(message = "CEP é obrigatório")
                    @Size(max = 20, message = "CEP deve ter no máximo 20 caracteres")
                    String postalCode,
            @Size(max = 150, message = "Contato deve ter no máximo 150 caracteres") String contact,
            @Size(max = 30, message = "Telefone deve ter no máximo 30 caracteres") String phone,
            @Size(max = 30, message = "Fax deve ter no máximo 30 caracteres") String fax,
            @Size(max = 500, message = "E-mails para nota fiscal devem ter no máximo 500 caracteres")
                    String invoiceEmails) {
        public Cobranca {
            company = aparar(company);
            cnpj = aparar(cnpj);
            stateRegistration = aparar(stateRegistration);
            street = aparar(street);
            city = aparar(city);
            state = aparar(state);
            district = aparar(district);
            postalCode = aparar(postalCode);
            contact = aparar(contact);
            phone = aparar(phone);
            fax = aparar(fax);
            invoiceEmails = aparar(invoiceEmails);
        }
    }

    public record Aprovacao(
            @NotBlank(message = "Nome do solicitante é obrigatório")
                    @Size(max = 150, message = "Nome do solicitante deve ter no máximo 150 caracteres")
                    String requesterName,
            @NotBlank(message = "Nome do responsável é obrigatório")
                    @Size(max = 150, message = "Nome do responsável deve ter no máximo 150 caracteres")
                    String responsibleName,
            @NotBlank(message = "Cargo é obrigatório")
                    @Size(max = 120, message = "Cargo deve ter no máximo 120 caracteres")
                    String jobTitle,
            @Size(max = 120, message = "Departamento deve ter no máximo 120 caracteres")
                    String department,
            @NotNull(message = "Data da aprovação é obrigatória") LocalDate date,
            boolean allowsResultsByEmail,
            @Email(message = "E-mail para envio dos resultados inválido")
                    @Size(max = 254, message = "E-mail para resultados deve ter no máximo 254 caracteres")
                    String resultsEmail) {
        public Aprovacao {
            requesterName = aparar(requesterName);
            responsibleName = aparar(responsibleName);
            jobTitle = aparar(jobTitle);
            department = aparar(department);
            resultsEmail = aparar(resultsEmail);
        }
    }

    private static String aparar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
