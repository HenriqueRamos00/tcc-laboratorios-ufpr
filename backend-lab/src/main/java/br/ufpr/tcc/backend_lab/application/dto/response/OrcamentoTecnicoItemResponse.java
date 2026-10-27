package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.OrcamentoItem;

public record OrcamentoTecnicoItemResponse(String testType, int sampleCount, String standard) {
    public static OrcamentoTecnicoItemResponse from(OrcamentoItem item) {
        return new OrcamentoTecnicoItemResponse(
                item.getTestType(), item.getSampleCount(), item.getStandard());
    }
}
