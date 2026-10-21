package br.ufpr.tcc.backend_auth.infrastructure.persistence;

import java.util.Locale;

public final class ViolacaoUnicidade {
    private ViolacaoUnicidade() {}

    public static boolean email(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null
                    && cause.getMessage().toLowerCase(Locale.ROOT).contains("uk_usuarios_email")) {
                return true;
            }
        }
        return false;
    }
}
