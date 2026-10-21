package br.ufpr.tcc.backend_auth.application.dto.response;

import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;

public record EmpresaResponse(String id, String name, String cnpj) {
    public static EmpresaResponse fromEntity(Empresa empresa) {
        return new EmpresaResponse(empresa.getId().toString(), empresa.getNome(), empresa.getCnpj());
    }
}
