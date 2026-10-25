package br.ufpr.tcc.backend_auth.application.dto.response;

public record TokenServicoEmitidoResponse(String accessToken, String tokenType, long expiresIn, TokenServicoResponse token) {
    @Override
    public String toString() {
        return "TokenServicoEmitidoResponse[accessToken=[REDACTED], token=" + token + "]";
    }
}
