package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.TokenServico;
import java.time.Instant;

public record TokenServicoResponse(String id, String name, String serviceId, Instant createdAt, Instant expiresAt) {
    public static TokenServicoResponse fromEntity(TokenServico token) {
        return new TokenServicoResponse(token.getId().toString(), token.getNome(), token.getServico().getId().toString(),
                token.getCriadoEm(), token.getExpiraEm());
    }
}
