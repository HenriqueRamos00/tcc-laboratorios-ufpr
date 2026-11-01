package br.ufpr.tcc.backend_lab.infrastructure.persistence.converter;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.StatusEnsaioConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusEnsaioConverterTests {
    private final StatusEnsaioConverter converter = new StatusEnsaioConverter();

    @Test
    void persisteECarregaOsSeteCodigosTecnicos() {
        for (StatusEnsaio status : StatusEnsaio.values()) {
            assertEquals(status.codigo(), converter.convertToDatabaseColumn(status));
            assertEquals(status, converter.convertToEntityAttribute(status.codigo()));
        }
    }

    @Test
    void rejeitaCodigoDesconhecidoSemFallback() {
        assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("IN_PROGRESS"));
    }
}
