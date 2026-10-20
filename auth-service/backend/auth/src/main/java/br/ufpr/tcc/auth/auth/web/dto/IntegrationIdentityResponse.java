package br.ufpr.tcc.auth.auth.web.dto;

import java.time.LocalDateTime;

public record IntegrationIdentityResponse(String id, String name, String prefix,
                                          LocalDateTime expiresAt) {
}
