package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;

public record AdministradorResponse(String id, String name, String email) {
    public static AdministradorResponse fromEntity(Usuario usuario) {
        return new AdministradorResponse(usuario.getId().toString(), usuario.getNome(), usuario.getEmail());
    }
}
