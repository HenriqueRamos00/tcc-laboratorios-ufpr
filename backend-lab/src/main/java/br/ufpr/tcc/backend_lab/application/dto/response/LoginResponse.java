package br.ufpr.tcc.backend_lab.application.dto.response;

public record LoginResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		AuthenticatedUserResponse user
) {
}
