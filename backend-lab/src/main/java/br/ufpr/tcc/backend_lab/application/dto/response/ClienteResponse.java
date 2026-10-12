package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;

import java.util.Comparator;
import java.util.List;

public record ClienteResponse(
        String id,
        String name,
        String email,
        String phone,
        String position,
        CadastroStatus status,
        List<EmpresaVinculadaResponse> companies) {
    public static ClienteResponse from(Cliente cliente) {
        var usuario = cliente.getUsuario();
        return new ClienteResponse(
                cliente.getId().toString(),
                usuario.getNome(),
                usuario.getEmail(),
                cliente.getPhone(),
                cliente.getPosition(),
                CadastroStatus.from(usuario.isAtivo()),
                cliente.getEmpresas().stream()
                        .sorted(Comparator.comparing(Empresa::getId))
                        .map(EmpresaVinculadaResponse::from)
                        .toList());
    }
}
