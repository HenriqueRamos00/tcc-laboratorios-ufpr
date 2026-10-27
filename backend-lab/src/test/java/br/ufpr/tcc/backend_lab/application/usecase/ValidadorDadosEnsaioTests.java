package br.ufpr.tcc.backend_lab.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorDadosEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidadorDadosEnsaioTests {
    private final ValidadorDadosEnsaio validador = new ValidadorDadosEnsaio();

    @Test
    void rejeitaMaisDeDezesseisDigitosInteirosSemArredondar() {
        assertThrows(DadosEnsaioInvalidosException.class,
                () -> validador.validarMedicoes(List.of(medicao("h2", "quantity", "99999999999999999"))));
    }

    @Test
    void rejeitaMaisDeOitoCasasDecimais() {
        assertThrows(DadosEnsaioInvalidosException.class,
                () -> validador.validarMedicoes(List.of(medicao("h2", "quantity", "0.123456789"))));
    }

    @Test
    void rejeitaCodigoDeMedicaoDuplicadoSemDistinguirCaixa() {
        assertThrows(DadosEnsaioInvalidosException.class, () -> validador.validarMedicoes(List.of(
                medicao("H2", "quantity", "0"), medicao("h2", "quantity", "1"))));
    }

    @Test
    void rejeitaMediacaoNulaComErroDeEntrada() {
        assertThrows(DadosEnsaioInvalidosException.class,
                () -> validador.validarMedicoes(java.util.Arrays.asList((EnsaioRequest.MeasurementRequest) null)));
    }

    @Test
    void preservaZeroComoQuantidadeEAsOutrasFormasDeResultado() {
        var medicoes = validador.validarMedicoes(List.of(
                medicao("zero", "quantity", "0"), medicao("nd", "not-detected", null),
                medicao("np", "not-performed", null), medicaoTexto("texto", "0,01%")));
        assertEquals("0", medicoes.getFirst().getQuantidade().toPlainString());
        assertEquals("not-detected", medicoes.get(1).getTipoResultado());
        assertEquals("not-performed", medicoes.get(2).getTipoResultado());
        assertEquals("0,01%", medicoes.get(3).getResultadoTextual());
    }

    private EnsaioRequest.MeasurementRequest medicao(String code, String kind, String value) {
        return new EnsaioRequest.MeasurementRequest(code, "Resultado", "ppm", null, null,
                new EnsaioRequest.ResultRequest(kind, value, null));
    }

    private EnsaioRequest.MeasurementRequest medicaoTexto(String code, String text) {
        return new EnsaioRequest.MeasurementRequest(code, "Resultado textual", null, null, null,
                new EnsaioRequest.ResultRequest("text", null, text));
    }
}
