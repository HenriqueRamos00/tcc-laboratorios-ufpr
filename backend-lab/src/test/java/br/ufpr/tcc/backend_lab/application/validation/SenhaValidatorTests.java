package br.ufpr.tcc.backend_lab.application.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SenhaValidatorTests {
    private final SenhaValidator validador = new SenhaValidator();

    @Test
    @DisplayName("aceita senha no limite de caracteres e com acentos")
    void aceitaSenhaNoLimiteEComAcentos() {
        assertDoesNotThrow(() -> validador.validar("a".repeat(72)));
        assertDoesNotThrow(() -> validador.validar("é".repeat(36)));
    }

    @Test
    @DisplayName("rejeita senha que excede 72 caracteres")
    void rejeitaSenhaAcimaDoLimiteDeCaracteres() {
        RegraNegocioException excecao =
                assertThrows(RegraNegocioException.class, () -> validador.validar("a".repeat(73)));

        assertEquals("Senha deve ter no máximo 72 caracteres", excecao.getMessage());
    }

    @Test
    @DisplayName("retorna mensagem simples quando a senha excede a capacidade do BCrypt")
    void preservaLimiteDoEncoderSemExporDetalhesTecnicos() {
        RegraNegocioException excecao =
                assertThrows(RegraNegocioException.class, () -> validador.validar("é".repeat(37)));

        assertEquals("Senha muito longa. Use uma senha mais curta.", excecao.getMessage());
    }

    @Test
    @DisplayName("rejeita senha em branco e preserva senha ausente opcional")
    void validaSenhaEmBrancoSemRejeitarNulo() {
        RegraNegocioException excecao =
                assertThrows(RegraNegocioException.class, () -> validador.validar("  "));
        assertEquals("Senha não pode estar em branco", excecao.getMessage());
        assertDoesNotThrow(() -> validador.validar(null));
    }
}
