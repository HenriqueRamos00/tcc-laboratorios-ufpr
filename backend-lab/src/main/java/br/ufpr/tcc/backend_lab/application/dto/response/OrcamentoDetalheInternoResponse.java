package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrcamentoDetalheInternoResponse(
        String id,
        String code,
        String name,
        String status,
        String stage,
        String description,
        String companyId,
        String companyName,
        String laboratoryName,
        String scope,
        String externalContactName,
        String externalContactEmail,
        BigDecimal totalPrice,
        OffsetDateTime createdDate,
        boolean hasDocument,
        List<OrcamentoItemResponse> items) {
    public static OrcamentoDetalheInternoResponse from(
            Orcamento orcamento, boolean hasDocument) {
        var empresa = orcamento.getEmpresa();
        return new OrcamentoDetalheInternoResponse(
                orcamento.getId().toString(),
                orcamento.getCode(),
                orcamento.getName(),
                orcamento.getStatus().codigo(),
                orcamento.getStage(),
                orcamento.getDescription(),
                empresa.getId().toString(),
                empresa.getLegalName(),
                orcamento.getLaboratoryName(),
                orcamento.getScope(),
                orcamento.getExternalContactName() == null
                        ? empresa.getContactName()
                        : orcamento.getExternalContactName(),
                orcamento.getExternalContactEmail() == null
                        ? empresa.getContactEmail()
                        : orcamento.getExternalContactEmail(),
                orcamento.getTotalPrice(),
                orcamento.getCreatedDate(),
                hasDocument,
                orcamento.getItems().stream().map(OrcamentoItemResponse::from).toList());
    }
}
