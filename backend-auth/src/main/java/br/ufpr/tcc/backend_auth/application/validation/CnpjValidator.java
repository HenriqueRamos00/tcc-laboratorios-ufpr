package br.ufpr.tcc.backend_auth.application.validation;

import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CnpjValidator {
    public static String normalizar(String cnpj) {
        if (cnpj == null) return null;
        String valor = cnpj.trim().toUpperCase(Locale.ROOT);
        if (valor.matches("[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-[0-9]{2}")) {
            return valor.replace(".", "").replace("/", "").replace("-", "");
        }
        return valor;
    }

    public void validar(String cnpj) {
        if (cnpj == null || !cnpj.matches("[A-Z0-9]{12}[0-9]{2}") || cnpj.matches("([0-9])\\1{13}")
                || digito(cnpj.substring(0, 12)) != cnpj.charAt(12) - '0'
                || digito(cnpj.substring(0, 13)) != cnpj.charAt(13) - '0') {
            throw new DadosInvalidosException("CNPJ inválido");
        }
    }

    private int digito(String base) {
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
