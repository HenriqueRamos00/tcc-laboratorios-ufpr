package br.ufpr.tcc.backend_auth.application.validation;

import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class SenhaValidator {
    public void validar(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new DadosInvalidosException("Senha é obrigatória");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new DadosInvalidosException("Senha deve ter no máximo 72 bytes UTF-8");
        }
    }
}
