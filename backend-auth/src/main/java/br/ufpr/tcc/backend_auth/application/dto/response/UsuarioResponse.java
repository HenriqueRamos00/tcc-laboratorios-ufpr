package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;

public record UsuarioResponse(String id, String name, String email, PerfilUsuario role) {
    public static UsuarioResponse fromEntity(Usuario usuario) {
        return new UsuarioResponse(usuario.getId().toString(), usuario.getNome(),
                usuario.getEmail(), usuario.getPerfil());
    }
}
