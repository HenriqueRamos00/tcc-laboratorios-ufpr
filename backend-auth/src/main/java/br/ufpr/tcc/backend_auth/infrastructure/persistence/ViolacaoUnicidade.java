package br.ufpr.tcc.backend_auth.infrastructure.persistence;

import java.util.Locale;

public final class ViolacaoUnicidade {
    private ViolacaoUnicidade() {}

    public static boolean email(Throwable failure) {
        return restricao(failure, "uk_usuarios_email");
    }

    public static boolean codigoServico(Throwable failure) {
        return restricao(failure, "uk_servicos_codigo");
    }

    public static boolean cnpj(Throwable failure) {
        return restricao(failure, "uk_empresas_cnpj");
    }

    private static boolean restricao(Throwable failure, String nome) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null
                    && cause.getMessage().toLowerCase(Locale.ROOT).contains(nome)) {
                return true;
            }
        }
        return false;
    }
}
