package br.ufpr.tcc.auth.auth.model;

import java.time.LocalDateTime;

public record IntegrationPrincipal(String apiKeyId, String name, String keyPrefix,
                                   LocalDateTime expiresAt) {
}
