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
 * O incremento acontece dentro do comando e a checagem do bloqueio vive no mesmo WHERE.
 * Isso resolve as duas corridas de uma vez: nao existe leitura seguida de escrita para se
 * perder, e nao existe janela entre consultar e agir. O relogio e o do banco, para que
 * duas instancias nao discordem sobre quando o bloqueio expira.
 */
@Component
class LoginAttemptLedger {

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
     */
    private static final String REGISTER_FAILURE = """
            UPDATE LOGIN_ATTEMPT
               SET FAILED_ATTEMPTS = FAILED_ATTEMPTS + 1,
                   LAST_FAILURE_AT = SYSTIMESTAMP,
                   LOCKED_UNTIL = CASE
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

    LoginAttemptOutcome registerFailure(long applicationId, String identifier, Duration cooldown) {
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
    void registerSuccess(long applicationId, String identifier) {
        jdbcClient.sql("UPDATE LOGIN_ATTEMPT SET FAILED_ATTEMPTS = 0, LOCKED_UNTIL = NULL"
                        + " WHERE ATTEMPT_KEY = :attemptKey")
                .param("attemptKey", attemptKey(applicationId, identifier))
                .update();
    }

    /** Estado atual do bloqueio, sem contar tentativa. */
    LoginAttemptOutcome currentOutcome(long applicationId, String identifier) {
        return currentOutcome(attemptKey(applicationId, identifier));
    }

    int failedAttemptsOf(long applicationId, String identifier) {
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
