package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginUsuarioResponse(String id, String name, String email, PerfilUsuario role,
        @Schema(example = "portal-web") String serviceCode, AutorizacoesUsuarioResponse authorizations) {
    public static LoginUsuarioResponse fromEntity(Usuario usuario) {
        return new LoginUsuarioResponse(usuario.getId().toString(), usuario.getNome(),
                usuario.getEmail(), usuario.getPerfil(), usuario.getServico().getCodigo(), AutorizacoesUsuarioResponse.fromEntity(usuario));
    }
}
