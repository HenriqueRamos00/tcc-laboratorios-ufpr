package br.ufpr.tcc.backend_auth.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginAdministradorResponse(
        @Schema(description = "Token opaco para operações administrativas; não é JWT.") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "3600", description = "Validade em segundos.") long expiresIn,
        AdministradorResponse user) {
    @Override
    public String toString() {
        return "LoginAdministradorResponse[accessToken=[REDACTED], tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + ", user=" + user + "]";
    }
}
