package br.ufpr.tcc.auth.auth.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(String id, String name, String email, String role,
                           boolean enabled, List<CompanyRefResponse> companies,
                           LocalDateTime createdAt) {
}
