package br.ufpr.tcc.backend_lab.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidadorEmpresaTests {
    private static ValidatorFactory validatorFactory;
    private static Validator beanValidator;

    private ValidadorEmpresa validador;

    @BeforeAll
    static void criarValidadorBean() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        beanValidator = validatorFactory.getValidator();
    }

    @AfterAll
    static void fecharValidadorBean() {
        validatorFactory.close();
    }

    @BeforeEach
    void criarValidadorEmpresa() {
        validador = new ValidadorEmpresa(beanValidator);
    }

    @Test
    @DisplayName("normaliza CNPJ numérico sem máscara e com máscara exata")
    void normalizaFormatosCnpjAceitos() {
        assertEquals("00000001000136", validador.normalizarCnpj("00000001000136"));
        assertEquals("00000001000136", validador.normalizarCnpj("00.000.001/0001-36"));
    }

    @Test
    @DisplayName("recusa formatos CNPJ alfanuméricos e máscaras parciais")
    void recusaFormatosCnpjNaoSuportados() {
        assertFormatoInvalido("AB000001000136");
        assertFormatoInvalido("00.000001/0001-36");
        assertFormatoInvalido("00.000.001/0001/36");
    }

    @Test
    @DisplayName("recusa dígitos verificadores inválidos e sequências repetidas")
    void recusaCnpjComDigitosInvalidosOuRepetidos() {
        assertCnpjInvalido("00000001000137");
        assertCnpjInvalido("00000000000000");
        assertCnpjInvalido("11111111111111");
    }

    @Test
    @DisplayName("aceita somente siglas de UFs brasileiras")
    void validaUfBrasileira() {
        validador.validarUf("PR");

        RegraNegocioException excecao =
                assertThrows(RegraNegocioException.class, () -> validador.validarUf("XX"));
        assertEquals(RegraNegocioException.Motivo.DADOS_INVALIDOS, excecao.getMotivo());
        assertEquals("UF inválida", excecao.getMessage());
    }

    private void assertFormatoInvalido(String cnpj) {
        RegraNegocioException excecao =
                assertThrows(RegraNegocioException.class, () -> validador.normalizarCnpj(cnpj));
        assertEquals("CNPJ deve conter 14 dígitos ou a máscara 00.000.000/0000-00", excecao.getMessage());
    }

    private void assertCnpjInvalido(String cnpj) {
        RegraNegocioException excecao =
                assertThrows(RegraNegocioException.class, () -> validador.normalizarCnpj(cnpj));
        assertEquals("CNPJ inválido", excecao.getMessage());
    }
}
