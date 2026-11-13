package br.ufpr.tcc.auth.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
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

    private final LoginAttemptRepository attempts;

    LoginAttemptLedger(LoginAttemptRepository attempts) {
        this.attempts = attempts;
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
        attempts.registerFailure(attemptKey, MAXIMUM_FAILURES, cooldown.toSeconds());
        return currentOutcome(attemptKey);
    }

    public void registerSuccess(long applicationId, String identifier) {
        attempts.clearFailures(attemptKey(applicationId, identifier));
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
        return attempts.readFailedAttempts(attemptKey(applicationId, identifier)).orElse(0);
    }

    /**
     * Garante a linha do contador, tolerando a corrida.
     *
     * Duas tentativas simultaneas do mesmo identificador podem criar a linha ao mesmo tempo.
     * Quem perdeu a corrida encontra a linha pronta, que e exatamente o estado desejado, entao
     * a violacao de chave aqui nao e falha: e a outra requisicao tendo feito o trabalho.
     *
     * O tratamento pode ficar nesta altura porque nenhuma entrada do servico abre transacao
     * em volta do login: a gravacao acontece na transacao do proprio comando, que e desfeita
     * sozinha, e nada depois dela depende daquela transacao continuar viva.
     */
    private void ensureRowExists(byte[] attemptKey) {
        try {
            attempts.ensureRowExists(attemptKey);
        } catch (DataIntegrityViolationException concurrentInsert) {
            return;
        }
    }

    private LoginAttemptOutcome currentOutcome(byte[] attemptKey) {
        return attempts.readLockWindow(attemptKey)
                .map(LoginAttemptLedger::outcomeOf)
                .orElseGet(LoginAttemptOutcome::allowed);
    }

    /**
     * Um segundo e o piso de proposito. O consumidor le o prazo como delta em segundos
     * inteiros e trata zero como ausencia de espera, entao um bloqueio que ainda vale por
     * menos de um segundo precisa sair como um, e nao como zero.
     */
    private static LoginAttemptOutcome outcomeOf(LockWindow window) {
        if (window.lockedUntil() == null
                || !window.lockedUntil().isAfter(window.databaseNow())) {
            return LoginAttemptOutcome.allowed();
        }
        long remaining = Duration.between(window.databaseNow(), window.lockedUntil()).toSeconds();
        return new LoginAttemptOutcome(true, Math.max(1L, remaining));
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
