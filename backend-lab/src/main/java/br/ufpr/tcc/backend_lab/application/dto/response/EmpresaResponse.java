package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;

public record EmpresaResponse(
        String id,
        String legalName,
        String cnpj,
        String address,
        String city,
        String state,
        String contactName,
        String contactPhone,
        String contactEmail,
        CadastroStatus status,
        String origin) {
    public static EmpresaResponse from(Empresa empresa) {
        return new EmpresaResponse(
                empresa.getId().toString(),
                empresa.getLegalName(),
                empresa.getCnpj(),
                empresa.getAddress(),
                empresa.getCity(),
                empresa.getState(),
                empresa.getContactName(),
                empresa.getContactPhone(),
                empresa.getContactEmail(),
                CadastroStatus.from(empresa.isAtivo()),
                empresa.getOrigin());
    }
}
