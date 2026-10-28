package br.ufpr.tcc.auth.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Contador de tentativa de login, que sustenta a regra das cinco tentativas.
 *
 * A chave e o digest de aplicacao mais identificador apresentado, nunca a conta. Assim um
 * e-mail que nao existe no cadastro e contado igual a um que existe, e sem isso o codigo
 * de bloqueio passaria a significar que a conta existe.
 *
 * Ele atende as duas entradas do servico, a de cliente e a administrativa, em espacos de
 * chave separados. Separados importa: com espaco unico, um cliente de qualquer aplicacao com o
 * mesmo endereco de um administrador poderia tranca lo de fora, e o contrario tambem.
 *
 * O incremento acontece dentro do comando e a checagem do bloqueio vive no mesmo WHERE.
 * Isso resolve as duas corridas de uma vez: nao existe leitura seguida de escrita para se
 * perder, e nao existe janela entre consultar e agir. O relogio e o do banco, para que
 * duas instancias nao discordem sobre quando o bloqueio expira.
 */
@Component
public class LoginAttemptLedger {

    /** Regra de negocio do Lactec: cinco tentativas. */
    private static final int MAXIMUM_FAILURES = 5;

    private static final String ENSURE_ROW = """
            MERGE INTO LOGIN_ATTEMPT target
            USING (SELECT :attemptKey AS attempt_key FROM DUAL) source
            ON (target.ATTEMPT_KEY = source.attempt_key)
            WHEN NOT MATCHED THEN INSERT (ATTEMPT_KEY, FAILED_ATTEMPTS) VALUES (source.attempt_key, 0)
            """;

    /**
     * Debita primeiro e decide depois. A clausula de bloqueio no proprio WHERE faz o
     * comando nao afetar linha nenhuma quando a conta ja esta trancada, e o numero de
     * linhas afetadas vira a decisao, sem segunda consulta no meio.
     *
     * Espera vencida recomeca a contagem, e esse ramo e a diferenca entre a regra do Lactec
     * e prisao perpetua. Sem ele, so o acerto de senha zerava o contador: a primeira falha
     * depois da espera levava a contagem de cinco para seis, o limite aparecia estourado
     * outra vez, e a conta trancava pelo periodo inteiro. Quem nao sabe a senha gastava uma
     * requisicao por periodo, em vez de cinco, para manter conta alheia fechada; e o titular
     * que errasse a digitacao uma vez nunca mais sairia do ciclo. Como efeito de borda, o
     * contador tambem deixa de crescer sem teto: ele passa a viver entre zero e cinco, e
     * FAILED_ATTEMPTS nao caminha mais para o limite de NUMBER(3), que estouraria como erro
     * de servidor na rota de login.
     */
    private static final String REGISTER_FAILURE = """
            UPDATE LOGIN_ATTEMPT
               SET FAILED_ATTEMPTS = CASE
                       WHEN LOCKED_UNTIL IS NOT NULL AND LOCKED_UNTIL <= SYSTIMESTAMP THEN 1
                       ELSE FAILED_ATTEMPTS + 1
                   END,
                   LAST_FAILURE_AT = SYSTIMESTAMP,
                   LOCKED_UNTIL = CASE
                       WHEN LOCKED_UNTIL IS NOT NULL AND LOCKED_UNTIL <= SYSTIMESTAMP THEN NULL
                       WHEN FAILED_ATTEMPTS + 1 >= :maximumFailures
                       THEN SYSTIMESTAMP + NUMTODSINTERVAL(:cooldownSeconds, 'SECOND')
                       ELSE LOCKED_UNTIL
                   END
             WHERE ATTEMPT_KEY = :attemptKey
               AND (LOCKED_UNTIL IS NULL OR LOCKED_UNTIL <= SYSTIMESTAMP)
            """;

    private static final String READ_LOCK = """
            SELECT LOCKED_UNTIL, SYSTIMESTAMP AS DATABASE_NOW
              FROM LOGIN_ATTEMPT
             WHERE ATTEMPT_KEY = :attemptKey
            """;

    private final JdbcClient jdbcClient;

    LoginAttemptLedger(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Espaco de chave da entrada administrativa.
     *
     * Zero nao colide com aplicacao nenhuma: identificador de aplicacao e TSID, que carrega o
     * instante de criacao nos bits altos e por isso nunca e zero. Usar um valor reservado em
     * vez de uma coluna nova mantem o contador com uma chave so, que e o que permite o mesmo
     * comando atomico servir as duas entradas.
     */
    public static final long ADMINISTRATIVE_SCOPE = 0L;

    public LoginAttemptOutcome registerFailure(long applicationId, String identifier,
            Duration cooldown) {
        byte[] attemptKey = attemptKey(applicationId, identifier);
        ensureRowExists(attemptKey);
        jdbcClient.sql(REGISTER_FAILURE)
                .param("maximumFailures", MAXIMUM_FAILURES)
                .param("cooldownSeconds", cooldown.toSeconds())
                .param("attemptKey", attemptKey)
                .update();
        return currentOutcome(attemptKey);
    }

    /**
     * Sucesso zera a contagem. Sem isso, cinco tentativas significariam cinco desde o
     * ultimo acerto, e uma conta pouco usada que erra uma vez por mes trancaria sozinha
     * depois de cinco meses.
     */
    public void registerSuccess(long applicationId, String identifier) {
        jdbcClient.sql("UPDATE LOGIN_ATTEMPT SET FAILED_ATTEMPTS = 0, LOCKED_UNTIL = NULL"
                        + " WHERE ATTEMPT_KEY = :attemptKey")
                .param("attemptKey", attemptKey(applicationId, identifier))
                .update();
    }

    /** Estado atual do bloqueio, sem contar tentativa. */
    public LoginAttemptOutcome currentOutcome(long applicationId, String identifier) {
        return currentOutcome(attemptKey(applicationId, identifier));
    }

    /**
     * Quantas falhas estao debitadas para aquele identificador.
     *
     * Publico porque teste de contrato, que vive noutro pacote, precisa afirmar sobre o
     * contador **pela producao**: a alternativa seria o teste recalcular a chave digerida, e
     * ai ele passaria a concordar com qualquer mudanca de formato de chave em vez de acusa-la.
     */
    public int failedAttemptsOf(long applicationId, String identifier) {
        return jdbcClient.sql("SELECT FAILED_ATTEMPTS FROM LOGIN_ATTEMPT WHERE ATTEMPT_KEY = :attemptKey")
                .param("attemptKey", attemptKey(applicationId, identifier))
                .query(Integer.class).optional().orElse(0);
    }

    /**
     * Garante a linha do contador, tolerando a corrida.
     *
     * Duas tentativas simultaneas do mesmo identificador podem criar a linha ao mesmo tempo.
     * Quem perdeu a corrida encontra a linha pronta, que e exatamente o estado desejado, entao
     * a violacao de chave aqui nao e falha: e a outra requisicao tendo feito o trabalho.
     */
    private void ensureRowExists(byte[] attemptKey) {
        try {
            jdbcClient.sql(ENSURE_ROW).param("attemptKey", attemptKey).update();
        } catch (DuplicateKeyException concurrentInsert) {
            return;
        }
    }

    private LoginAttemptOutcome currentOutcome(byte[] attemptKey) {
        return jdbcClient.sql(READ_LOCK).param("attemptKey", attemptKey)
                .query((resultSet, rowNumber) -> {
                    OffsetDateTime lockedUntil =
                            resultSet.getObject("LOCKED_UNTIL", OffsetDateTime.class);
                    OffsetDateTime databaseNow =
                            resultSet.getObject("DATABASE_NOW", OffsetDateTime.class);
                    if (lockedUntil == null || !lockedUntil.isAfter(databaseNow)) {
                        return LoginAttemptOutcome.allowed();
                    }
                    long remaining = Duration.between(databaseNow, lockedUntil).toSeconds();
                    return new LoginAttemptOutcome(true, Math.max(1L, remaining));
                })
                .optional().orElseGet(LoginAttemptOutcome::allowed);
    }

    /**
     * O identificador apresentado nao fica legivel no banco: a tabela de controle nao pode
     * virar lista de quem tentou entrar. A aplicacao entra na chave para que o mesmo
     * e-mail em duas aplicacoes tenha contagem propria.
     */
    private static byte[] attemptKey(long applicationId, String identifier) {
        String material = applicationId + "\u0000" + identifier.toLowerCase(Locale.ROOT);
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException absentAlgorithm) {
            throw new IllegalStateException(
                    "SHA-256 e exigido de toda implementacao da plataforma Java", absentAlgorithm);
        }
    }
}
