package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;

public record EmpresaEquipamentoResponse(String id, String legalName, String cnpj, CadastroStatus status) {
    public static EmpresaEquipamentoResponse from(Empresa empresa) {
        return new EmpresaEquipamentoResponse(empresa.getId().toString(), empresa.getLegalName(),
                empresa.getCnpj(), CadastroStatus.from(empresa.isAtivo()));
    }
}
