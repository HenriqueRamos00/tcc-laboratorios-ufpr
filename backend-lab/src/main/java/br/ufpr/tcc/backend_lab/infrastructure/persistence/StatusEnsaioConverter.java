package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StatusEnsaioConverter implements AttributeConverter<StatusEnsaio, String> {
    @Override
    public String convertToDatabaseColumn(StatusEnsaio attribute) {
        return attribute == null ? null : attribute.codigo();
    }

    @Override
    public StatusEnsaio convertToEntityAttribute(String value) {
        return value == null ? null : StatusEnsaio.deCodigo(value);
    }
}
