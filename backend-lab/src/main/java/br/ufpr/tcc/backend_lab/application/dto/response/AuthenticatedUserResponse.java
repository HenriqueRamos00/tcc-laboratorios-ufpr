package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;

public record AuthenticatedUserResponse(
		Long id,
		String name,
		String email,
		String role
) {

	public static AuthenticatedUserResponse fromEntity(Usuario usuario) {
		return new AuthenticatedUserResponse(
				usuario.getId(),
				usuario.getNome(),
				usuario.getEmail(),
				usuario.getPerfil().name()
		);
	}
}
