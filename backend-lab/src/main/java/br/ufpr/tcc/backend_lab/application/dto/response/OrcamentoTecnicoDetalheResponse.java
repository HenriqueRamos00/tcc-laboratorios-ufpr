package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusOrcamentoTecnico;

import java.time.OffsetDateTime;
import java.util.List;

public record OrcamentoTecnicoDetalheResponse(
        String id,
        String code,
        String name,
        String companyName,
        OffsetDateTime createdDate,
        String technicalStatus,
        String description,
        String laboratoryName,
        String scope,
        List<OrcamentoTecnicoItemResponse> items) {
    public static OrcamentoTecnicoDetalheResponse from(
            Orcamento orcamento, StatusOrcamentoTecnico technicalStatus) {
        return new OrcamentoTecnicoDetalheResponse(
                orcamento.getId().toString(),
                orcamento.getCode(),
                orcamento.getName(),
                orcamento.getEmpresa().getLegalName(),
                orcamento.getCreatedDate(),
                technicalStatus == null ? null : technicalStatus.codigo(),
                orcamento.getDescription(),
                orcamento.getLaboratoryName(),
                orcamento.getScope(),
                orcamento.getItems().stream().map(OrcamentoTecnicoItemResponse::from).toList());
    }
}
