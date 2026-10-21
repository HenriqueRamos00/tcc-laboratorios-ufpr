package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import java.util.List;

public record AutorizacoesUsuarioResponse(List<String> companyCnpjs) {
    public static AutorizacoesUsuarioResponse fromEntity(Usuario usuario) {
        return new AutorizacoesUsuarioResponse(usuario.getEmpresas().stream().map(Empresa::getCnpj).sorted().toList());
    }
}
