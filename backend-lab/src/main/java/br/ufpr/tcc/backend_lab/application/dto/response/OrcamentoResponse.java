package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrcamentoResponse(
        String id,
        String code,
        String name,
        String status,
        String stage,
        String description,
        String companyName,
        String externalContactName,
        String externalContactEmail,
        BigDecimal totalPrice,
        OffsetDateTime createdDate) {
    public static OrcamentoResponse from(Orcamento orcamento) {
        var empresa = orcamento.getEmpresa();
        return new OrcamentoResponse(
                orcamento.getId().toString(),
                orcamento.getCode(),
                orcamento.getName(),
                orcamento.getStatus().codigo(),
                orcamento.getStage(),
                orcamento.getDescription(),
                empresa.getLegalName(),
                orcamento.getExternalContactName() == null
                        ? empresa.getContactName()
                        : orcamento.getExternalContactName(),
                orcamento.getExternalContactEmail() == null
                        ? empresa.getContactEmail()
                        : orcamento.getExternalContactEmail(),
                orcamento.getTotalPrice(),
                orcamento.getCreatedDate());
    }
}
