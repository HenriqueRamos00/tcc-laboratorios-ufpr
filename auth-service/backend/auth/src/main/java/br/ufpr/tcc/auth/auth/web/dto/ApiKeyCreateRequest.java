package br.ufpr.tcc.auth.auth.web.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyCreateRequest(
        @NotBlank @Size(max = 100) String name,
        LocalDateTime expiresAt) {
}
