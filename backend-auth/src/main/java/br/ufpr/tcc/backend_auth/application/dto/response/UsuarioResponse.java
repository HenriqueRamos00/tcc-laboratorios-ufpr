package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;
import java.util.List;

public record UsuarioResponse(String id, String name, String email, PerfilUsuario role, String serviceId, List<String> companyIds, AutorizacoesUsuarioResponse authorizations) {
    public static UsuarioResponse fromEntity(Usuario usuario) {
        return new UsuarioResponse(usuario.getId().toString(), usuario.getNome(),
                usuario.getEmail(), usuario.getPerfil(), usuario.getServico() == null ? null : usuario.getServico().getId().toString(),
                usuario.getEmpresas().stream().map(Empresa::getId).sorted().map(Object::toString).toList(),
                AutorizacoesUsuarioResponse.fromEntity(usuario));
    }
}
