package br.ufpr.tcc.backend_auth.application.validation;

import java.util.Locale;

public final class Normalizacao {
    private Normalizacao() {}

    public static String email(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
