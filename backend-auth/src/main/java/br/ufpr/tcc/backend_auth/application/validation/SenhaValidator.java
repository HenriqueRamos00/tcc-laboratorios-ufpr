package br.ufpr.tcc.backend_auth.application.validation;

import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class SenhaValidator {
    public void validarCliente(String senha) {
        validar(senha);
        if (senha.length() < 8 || !senha.codePoints().anyMatch(Character::isUpperCase)
                || !senha.codePoints().anyMatch(Character::isLowerCase)
                || !senha.codePoints().anyMatch(Character::isDigit)) {
            throw new DadosInvalidosException("Senha do cliente deve ter pelo menos 8 caracteres, uma letra maiúscula, uma minúscula e um número");
        }
    }

    public void validar(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new DadosInvalidosException("Senha é obrigatória");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new DadosInvalidosException("Senha deve ter no máximo 72 bytes UTF-8");
        }
    }
}
