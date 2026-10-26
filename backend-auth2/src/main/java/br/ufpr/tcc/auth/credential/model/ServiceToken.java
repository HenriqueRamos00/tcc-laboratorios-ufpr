package br.ufpr.tcc.auth.credential.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Token opaco que identifica uma aplicacao registrada. Ele vive em memoria pelo tempo de
 * uma emissao ou de uma verificacao: o que o banco guarda e o digest, e o valor em claro
 * nunca e gravado nem registrado em log.
 *
 * O token nao carrega identificador embutido. A forma identificador ponto verificador, que
 * o levantamento propos, esta descartada por medicao: o consumidor valida o token contra
 * uma expressao cujo conjunto permitido e o alfabeto base64 url-safe, onde ponto nao
 * existe, e qualquer caractere eleito separador tambem e caractere valido de segredo, o
 * que tornaria a divisao ambigua. A linha e encontrada pelo proprio digest, com restricao
 * unica na coluna, que e uma sondagem de indice.
 */
public final class ServiceToken {

    /**
     * Formato que o consumidor valida na inicializacao dele, transcrito de
     * Portal.Api/Configuration/PortalSourceRegistration.cs:65. Token fora deste formato
     * impede o Portal de subir, nao apenas de autenticar, entao ele e contrato e nao
     * preferencia nossa.
     */
    private static final Pattern CONSUMER_FORMAT = Pattern.compile("^svc_[A-Za-z0-9_-]{43}$");

    private static final String PREFIX = "svc_";

    /**
     * Trinta e dois bytes sao exatamente 43 caracteres em base64 url-safe sem preenchimento.
     * Este numero nao e ajustavel por gosto: um byte a mais ou a menos muda o comprimento do
     * texto e derruba o Portal na inicializacao.
     */
    private static final int SECRET_BYTE_COUNT = 32;

    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * SecureRandom se semeia sozinho e atende chamada concorrente. Nao se usa
     * getInstanceStrong: no Linux o algoritmo forte padrao pode bloquear esperando entropia,
     * e emissao de token ficaria parada.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String clearValue;

    private ServiceToken(String clearValue) {
        this.clearValue = clearValue;
    }

    /**
     * Emite um token novo. O valor devolvido e o unico momento em que ele existe em claro:
     * quem perder pede a reemissao.
     */
    public static ServiceToken generate() {
        byte[] secret = new byte[SECRET_BYTE_COUNT];
        RANDOM.nextBytes(secret);
        return new ServiceToken(PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
    }

    /**
     * Le o token que um chamador apresentou.
     *
     * Nada e normalizado. Espaco nas pontas recusa em vez de ser cortado, porque cortar
     * passaria a aceitar um valor que o consumidor nao consegue nem configurar: ele recusa
     * subir com ele.
     */
    public static ServiceToken fromPresentedValue(String presentedValue) {
        if (presentedValue == null || !CONSUMER_FORMAT.matcher(presentedValue).matches()) {
            throw new MalformedServiceTokenException();
        }
        return new ServiceToken(presentedValue);
    }

    /** Unico ponto que devolve o valor em claro, para a resposta da emissao. */
    public String clearValue() {
        return clearValue;
    }

    /**
     * Digest que o banco guarda. Hash rapido e correto aqui: o token tem 256 bits uniformes
     * de aleatoriedade, entao nao existe dicionario a percorrer, e por isso o custo
     * deliberado fica so na senha de pessoa.
     */
    public OpaqueTokenDigest digest() {
        try {
            MessageDigest algorithm = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return OpaqueTokenDigest.fromBytes(
                    algorithm.digest(clearValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException absentAlgorithm) {
            throw new IllegalStateException(
                    "SHA-256 e exigido de toda implementacao da plataforma Java", absentAlgorithm);
        }
    }

    /**
     * Nao devolve o segredo. toString aparece em log, em mensagem de erro e no depurador, e
     * token em log e exatamente o que a regra de log proibe.
     */
    @Override
    public String toString() {
        return "ServiceToken[valor omitido]";
    }
}
