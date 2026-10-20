package br.ufpr.tcc.auth.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 18) String cnpj) {
}
