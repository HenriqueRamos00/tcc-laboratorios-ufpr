package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;

import jakarta.validation.Validator;

import java.util.Set;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

import org.hibernate.validator.constraints.br.CNPJ;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ValidadorEmpresa {
    private static final Pattern FORMATO_CNPJ_ACEITO =
            Pattern.compile("(?:[0-9]{14}|[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2})");
    private static final Set<String> UFS_BRASILEIRAS =
            Set.of(
                    "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
                    "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC",
                    "SP", "SE", "TO");

    private final Validator validator;

    public String normalizarCnpj(String entrada) {
        if (entrada == null || !FORMATO_CNPJ_ACEITO.matcher(entrada).matches()) {
            throw RegraNegocioException.dadosInvalidos(
                    "CNPJ deve conter 14 dígitos ou a máscara 00.000.000/0000-00");
        }

        String cnpj = entrada.replaceAll("[^0-9]", "");
        if (!validator.validate(new CnpjNumerico(cnpj)).isEmpty()) {
            throw RegraNegocioException.dadosInvalidos("CNPJ inválido");
        }

        return cnpj;
    }

    public void validarUf(String uf) {
        if (!UFS_BRASILEIRAS.contains(uf)) {
            throw RegraNegocioException.dadosInvalidos("UF inválida");
        }
    }

    private record CnpjNumerico(@CNPJ(format = CNPJ.Format.NUMERIC) String valor) {}
}
