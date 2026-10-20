package br.ufpr.tcc.auth.auth.web.dto;

import java.time.LocalDateTime;

public record CompanyResponse(String id, String name, String cnpj, int userCount,
                              LocalDateTime createdAt) {
}
