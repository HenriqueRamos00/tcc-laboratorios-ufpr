package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

@Component
public class SenhaValidator {
    private static final int LIMITE_CARACTERES = 72;
    private static final int LIMITE_BYTES_BCRYPT = 72;

    public void validar(String senha) {
        if (senha == null) {
            return;
        }
        if (senha.length() > LIMITE_CARACTERES) {
            throw RegraNegocioException.dadosInvalidos("Senha deve ter no máximo 72 caracteres");
        }
        if (senha.isBlank()) {
            throw RegraNegocioException.dadosInvalidos("Senha não pode estar em branco");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES_BCRYPT) {
            throw RegraNegocioException.dadosInvalidos(
                    "Senha muito longa. Use uma senha mais curta.");
        }
    }
}
