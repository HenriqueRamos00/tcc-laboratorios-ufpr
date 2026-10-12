package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;

public record TecnicoResponse(
		Long id,
		String name,
		String email,
		String unit,
		String specialty,
		TecnicoStatus status
) {
	public static TecnicoResponse fromEntity(Usuario usuario) {
		return new TecnicoResponse(
				usuario.getId(),
				usuario.getNome(),
				usuario.getEmail(),
				usuario.getUnidade(),
				usuario.getEspecialidade(),
				TecnicoStatus.from(usuario)
		);
	}
}
