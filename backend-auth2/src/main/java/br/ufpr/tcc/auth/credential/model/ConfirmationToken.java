package br.ufpr.tcc.auth.credential.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Credencial de uso unico que viaja por e-mail.
 *
 * Mesmo desenho dos outros tokens, com prefixo proprio: o prefixo diz onde procurar, e
 * adulterar o prefixo so faz a busca nao achar nada.
 */
public final class ConfirmationToken {

    public static final String PREFIX = "cnf_";

    private static final Pattern FORMAT = Pattern.compile("^cnf_[A-Za-z0-9_-]{43}$");

    private static final int SECRET_BYTE_COUNT = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String clearValue;

    private ConfirmationToken(String clearValue) {
        this.clearValue = clearValue;
    }

    public static ConfirmationToken generate() {
        byte[] secret = new byte[SECRET_BYTE_COUNT];
        RANDOM.nextBytes(secret);
        return new ConfirmationToken(
                PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
    }

    public static Optional<ConfirmationToken> fromPresentedValue(String presentedValue) {
        if (presentedValue == null || !FORMAT.matcher(presentedValue).matches()) {
            return Optional.empty();
        }
        return Optional.of(new ConfirmationToken(presentedValue));
    }

    public String clearValue() {
        return clearValue;
    }

    public OpaqueTokenDigest digest() {
        try {
            MessageDigest algorithm = MessageDigest.getInstance("SHA-256");
            return OpaqueTokenDigest.fromBytes(
                    algorithm.digest(clearValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException absentAlgorithm) {
            throw new IllegalStateException(
                    "SHA-256 e exigido de toda implementacao da plataforma Java", absentAlgorithm);
        }
    }

    @Override
    public String toString() {
        return "ConfirmationToken[valor omitido]";
    }
}
