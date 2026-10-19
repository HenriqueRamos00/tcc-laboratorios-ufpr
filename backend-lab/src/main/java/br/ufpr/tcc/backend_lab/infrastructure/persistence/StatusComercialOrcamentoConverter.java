package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StatusComercialOrcamentoConverter
        implements AttributeConverter<StatusComercialOrcamento, String> {
    @Override
    public String convertToDatabaseColumn(StatusComercialOrcamento status) {
        return status == null ? null : status.codigo();
    }

    @Override
    public StatusComercialOrcamento convertToEntityAttribute(String codigo) {
        if (codigo == null) {
            return null;
        }
        return StatusComercialOrcamento.fromCodigo(codigo)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Código de status comercial de orçamento desconhecido: "
                                                + codigo));
    }
}
