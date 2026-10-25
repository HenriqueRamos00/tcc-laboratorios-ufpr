package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;

public record AdministradorDetalheResponse(String id, String name, String email, boolean active) {
    public static AdministradorDetalheResponse fromEntity(Usuario usuario) {
        return new AdministradorDetalheResponse(usuario.getId().toString(), usuario.getNome(), usuario.getEmail(), usuario.isAtivo());
    }
}
