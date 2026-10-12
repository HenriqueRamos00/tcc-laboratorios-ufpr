package br.ufpr.tcc.backend_lab.infrastructure.persistence;

import java.util.Locale;

public final class ParametroLikeJpa {
    private static final char CARACTERE_ESCAPE_LIKE = '!';

    private ParametroLikeJpa() {}

    public static String buscaParcial(String busca) {
        if (busca == null || busca.isBlank()) {
            return null;
        }

        String termo = busca.trim().toLowerCase(Locale.ROOT);
        String escape = String.valueOf(CARACTERE_ESCAPE_LIKE);
        termo = termo.replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
        return "%" + termo + "%";
    }
}
