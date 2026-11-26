package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import org.springframework.stereotype.Component;

@Component
public class PasswordRecoveryPasswordPolicy {

    private static final int MINIMUM_LENGTH = 8;

    public void validate(String password) {
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (password.length() < MINIMUM_LENGTH || !hasUppercase || !hasLowercase || !hasDigit) {
            throw RegraNegocioException.dadosInvalidos(
                    "A senha deve ter ao menos 8 caracteres, uma letra maiúscula, uma minúscula e um número");
        }
    }
}
