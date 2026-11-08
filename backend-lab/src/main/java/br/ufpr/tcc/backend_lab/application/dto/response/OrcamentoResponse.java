package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.EtapaAcompanhamentoOrcamento;

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
        String protocol,
        String externalContactName,
        String externalContactEmail,
        BigDecimal totalPrice,
        OffsetDateTime createdDate) {
    public static OrcamentoResponse from(Orcamento orcamento, EtapaAcompanhamentoOrcamento etapa) {
        var empresa = orcamento.getEmpresa();
        return new OrcamentoResponse(
                orcamento.getId().toString(),
                orcamento.getCode(),
                orcamento.getName(),
                orcamento.getStatus().codigo(),
                etapa.texto(),
                orcamento.getDescription(),
                empresa.getLegalName(),
                orcamento.getProtocol(),
                contato(orcamento.getExternalContactName(), empresa.getContactName()),
                contato(orcamento.getExternalContactEmail(), empresa.getContactEmail()),
                orcamento.getTotalPrice(),
                orcamento.getCreatedDate());
    }

    private static String contato(String contatoExterno, String contatoEmpresarial) {
        String contatoFinal = contatoExterno == null ? contatoEmpresarial : contatoExterno;
        return contatoFinal == null ? "" : contatoFinal;
    }
}
