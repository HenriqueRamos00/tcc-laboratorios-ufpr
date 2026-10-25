package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.OrcamentoItem;

import java.math.BigDecimal;

public record OrcamentoItemResponse(
        String testType, Integer sampleCount, String standard, BigDecimal unitPrice) {
    public static OrcamentoItemResponse from(OrcamentoItem item) {
        return new OrcamentoItemResponse(
                item.getTestType(), item.getSampleCount(), item.getStandard(), item.getUnitPrice());
    }
}
