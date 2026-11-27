package br.ufpr.tcc.auth.shared.identifier;

import java.util.Optional;

/**
 * Le o identificador que chega como texto, no caminho ou no corpo.
 *
 * Texto que nao e numero e numero fora do intervalo de long viram vazio, e nao erro: para
 * quem chama, identificador que nao e numero e identificador que nao existe sao o mesmo
 * caso, e responder diferente contaria qual dos dois aconteceu.
 */
public final class Identifiers {

    private Identifiers() {
    }

    public static Optional<Long> parse(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
        }
    }

    /** Como parse, mas recusa zero e negativo, que o gerador de identificador nunca produz. */
    public static Optional<Long> parsePositive(String value) {
        return parse(value).filter(id -> id > 0);
    }
}
