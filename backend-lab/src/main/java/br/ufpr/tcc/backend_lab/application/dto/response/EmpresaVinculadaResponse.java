package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;

public record EmpresaVinculadaResponse(String id, String name, CadastroStatus status) {
    public static EmpresaVinculadaResponse from(Empresa empresa) {
        return new EmpresaVinculadaResponse(
                empresa.getId().toString(), empresa.getLegalName(), CadastroStatus.from(empresa.isAtivo()));
    }
}
