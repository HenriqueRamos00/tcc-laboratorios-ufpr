package br.ufpr.tcc.auth.credential.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Token opaco de uma sessao administrativa.
 *
 * Ele e separado do token de aplicacao por prefixo, e a separacao nao e cosmetica: o
 * prefixo diz em qual tabela procurar, e quem adulterar o prefixo simplesmente nao acha
 * linha nenhuma. O tipo efetivo sai sempre do registro encontrado, nunca do texto
 * apresentado.
 *
 * O formato segue o mesmo desenho do token de aplicacao, com a mesma entropia, porque a
 * credencial de quem administra nao pode ser mais fraca que a de quem consome.
 */
public final class AdministrativeToken {

    public static final String PREFIX = "adm_";

    private static final Pattern FORMAT = Pattern.compile("^adm_[A-Za-z0-9_-]{43}$");

    private static final int SECRET_BYTE_COUNT = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String clearValue;

    private AdministrativeToken(String clearValue) {
        this.clearValue = clearValue;
    }

    public static AdministrativeToken generate() {
        byte[] secret = new byte[SECRET_BYTE_COUNT];
        RANDOM.nextBytes(secret);
        return new AdministrativeToken(
                PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
    }

    /** Devolve vazio para valor malformado, em vez de estourar: recusa e recusa. */
    public static java.util.Optional<AdministrativeToken> fromPresentedValue(String presentedValue) {
        if (presentedValue == null || !FORMAT.matcher(presentedValue).matches()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new AdministrativeToken(presentedValue));
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
        return "AdministrativeToken[valor omitido]";
    }
}
