package br.ufpr.tcc.auth.credential.model;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Digest SHA-256 de um token de aplicacao. E este valor que a tabela guarda, e e por ele
 * que a linha e encontrada: o token nao carrega identificador embutido, porque o formato
 * que o consumidor exige nao tem lugar para separador.
 */
public final class OpaqueTokenDigest {

    /** SHA-256 produz 256 bits, e o comprimento fixo e o que mantem a comparacao uniforme. */
    private static final int BYTE_COUNT = 32;

    private static final int HEXADECIMAL_LENGTH = BYTE_COUNT * 2;

    private final byte[] value;

    private OpaqueTokenDigest(byte[] value) {
        this.value = value;
    }

    public static OpaqueTokenDigest fromBytes(byte[] value) {
        if (value == null || value.length != BYTE_COUNT) {
            throw new IllegalArgumentException("Digest de token precisa ter 32 bytes");
        }
        return new OpaqueTokenDigest(value.clone());
    }

    /**
     * Le o digest gravado. Comprimento ou caractere invalido aqui e dado nosso corrompido,
     * nao entrada de chamador, e por isso nao vira falha de autenticacao.
     */
    public static OpaqueTokenDigest fromHexadecimal(String hexadecimal) {
        if (hexadecimal == null || hexadecimal.length() != HEXADECIMAL_LENGTH) {
            throw new IllegalArgumentException("Digest gravado precisa ter 64 caracteres hexadecimais");
        }
        return fromBytes(HexFormat.of().parseHex(hexadecimal));
    }

    /**
     * Comparacao em tempo constante. MessageDigest.isEqual examina todos os bytes e o tempo
     * de calculo depende apenas do comprimento; Arrays.equals e String.equals param no
     * primeiro byte diferente e entregam, pelo tempo de resposta, quantos bytes o atacante
     * ja acertou.
     */
    public boolean matches(OpaqueTokenDigest other) {
        return other != null && MessageDigest.isEqual(this.value, other.value);
    }

    /** Forma que a coluna guarda. */
    public String asHexadecimal() {
        return HexFormat.of().formatHex(value);
    }

    /** Copia, porque devolver o vetor interno deixaria qualquer chamador reescrever o digest. */
    public byte[] asBytes() {
        return value.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OpaqueTokenDigest digest && matches(digest);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    /** O digest nao e credencial: quem o tem nao autentica sem a pre-imagem. */
    @Override
    public String toString() {
        return "OpaqueTokenDigest[" + asHexadecimal() + "]";
    }
}
