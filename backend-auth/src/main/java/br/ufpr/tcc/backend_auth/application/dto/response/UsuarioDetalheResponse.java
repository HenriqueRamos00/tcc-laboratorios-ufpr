package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;
import java.util.List;

public record UsuarioDetalheResponse(String id, String name, String email, PerfilUsuario role, boolean active, String serviceId, List<String> companyIds, AutorizacoesUsuarioResponse authorizations) {
    public static UsuarioDetalheResponse fromEntity(Usuario usuario) {
        return new UsuarioDetalheResponse(usuario.getId().toString(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(), usuario.isAtivo(), usuario.getServico() == null ? null : usuario.getServico().getId().toString(),
                usuario.getEmpresas().stream().map(Empresa::getId).sorted().map(Object::toString).toList(),
                AutorizacoesUsuarioResponse.fromEntity(usuario));
    }
}
