package br.ufpr.tcc.auth.auth.web.dto;

import java.time.LocalDateTime;

public record ApiKeyResponse(String id, String name, String keyPrefix, boolean enabled,
                             LocalDateTime createdAt, LocalDateTime expiresAt,
                             LocalDateTime lastUsedAt) {
}
