package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;

public record UsuarioDetalheResponse(String id, String name, String email, PerfilUsuario role, boolean active) {
    public static UsuarioDetalheResponse fromEntity(Usuario usuario) {
        return new UsuarioDetalheResponse(usuario.getId().toString(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(), usuario.isAtivo());
    }
}
