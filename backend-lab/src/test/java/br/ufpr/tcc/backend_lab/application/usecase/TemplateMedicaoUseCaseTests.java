package br.ufpr.tcc.backend_lab.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import org.junit.jupiter.api.Test;

class TemplateMedicaoUseCaseTests {
    private final TemplateMedicaoUseCase useCase = new TemplateMedicaoUseCase();

    @Test
    void disponibilizaLinhasDosFormatosConhecidosSemResultadosPreenchidos() {
        var fisicoQuimico = useCase.obter("physical-chemical");
        var cromatografia = useCase.obter("chromatography");

        assertEquals(6, fisicoQuimico.measurements().size());
        assertEquals(11, cromatografia.measurements().size());
    }

    @Test
    void rejeitaFormatoDesconhecido() {
        assertThrows(DadosEnsaioInvalidosException.class, () -> useCase.obter("outro"));
    }
}
