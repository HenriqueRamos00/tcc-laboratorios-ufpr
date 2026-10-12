package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;

public enum TecnicoStatus {
	ACTIVE,
	INACTIVE;

	public boolean toAtivo() {
		return this == ACTIVE;
	}

	public static TecnicoStatus from(Usuario usuario) {
		return usuario.isAtivo() ? ACTIVE : INACTIVE;
	}
}
