package br.ufpr.tcc.auth.authentication;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura e gravacao do contador de tentativa de login.
 *
 * Dois comandos aqui ficam em SQL, e os dois justificam o motivo no proprio Javadoc: eles
 * usam recurso do banco que a linguagem de consulta tipada nao expressa, e reescreve-los em
 * passos tipados traria de volta a corrida que eles existem para fechar. Os outros tres sao
 * consulta tipada, com parametro nomeado, e passam a ser conferidos na subida.
 *
 * Os tres comandos que gravam declaram transacao propria, e isso nao e redundancia. Nenhuma
 * das duas entradas do servico abre transacao em volta do login, de proposito: a verificacao
 * de senha custa 32 ms medidos, e um limite transacional em volta dela reteria uma conexao do
 * pool por esse tempo inteiro numa rota aberta. Medido na troca para JPA, sem esta declaracao
 * o contador respondia `No active transaction for update or delete query` e **toda** rota de
 * login caia com erro de servidor, porque o limite que o SQL a mao nao precisava passou a ser
 * exigido pela especificacao: gravacao por consulta exige transacao ativa. Uma por comando e
 * exatamente o que havia antes, quando cada comando ia ao banco em auto commit.
 */
interface LoginAttemptRepository extends JpaRepository<LoginAttempt, byte[]> {

    /**
     * Garante a linha do contador sem ler antes de gravar.
     *
     * Fica em SQL porque e um comando de insercao condicional, e a linguagem de consulta
     * tipada nao tem equivalente: a alternativa seria consultar e depois inserir, que e
     * justamente a corrida que este comando fecha. Duas tentativas simultaneas do mesmo
     * identificador podem chegar aqui ao mesmo tempo, e quem perde a corrida encontra a
     * linha pronta, que e o estado desejado.
     */
    @Transactional
    @Modifying
    @Query(value = """
            MERGE INTO LOGIN_ATTEMPT target
            USING (SELECT :attemptKey AS attempt_key FROM DUAL) source
            ON (target.ATTEMPT_KEY = source.attempt_key)
            WHEN NOT MATCHED THEN INSERT (ATTEMPT_KEY, FAILED_ATTEMPTS)
                 VALUES (source.attempt_key, 0)
            """, nativeQuery = true)
    void ensureRowExists(@Param("attemptKey") byte[] attemptKey);

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
     *
     * Fica em SQL por causa da aritmetica de prazo. O instante em que o bloqueio vence e
     * calculado a partir do relogio do banco mais o periodo de espera, e a linguagem de
     * consulta tipada nao soma um periodo que chega como parametro. Calcular esse instante em
     * Java traria o relogio da aplicacao de volta para dentro da regra, e a comparacao que
     * roda depois e feita contra o relogio do banco: com as duas pontas em relogios
     * diferentes, a janela de espera muda de tamanho conforme a maquina que atendeu.
     */
    @Transactional
    @Modifying
    @Query(value = """
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
            """, nativeQuery = true)
    void registerFailure(@Param("attemptKey") byte[] attemptKey,
            @Param("maximumFailures") int maximumFailures,
            @Param("cooldownSeconds") long cooldownSeconds);

    @Query("""
            select new br.ufpr.tcc.auth.authentication.LockWindow(
                   attempt.lockedUntil, offset datetime)
              from LoginAttempt attempt
             where attempt.attemptKey = :attemptKey
            """)
    Optional<LockWindow> readLockWindow(@Param("attemptKey") byte[] attemptKey);

    /**
     * Sucesso zera a contagem. Sem isso, cinco tentativas significariam cinco desde o
     * ultimo acerto, e uma conta pouco usada que erra uma vez por mes trancaria sozinha
     * depois de cinco meses.
     */
    @Transactional
    @Modifying
    @Query("""
            update LoginAttempt attempt
               set attempt.failedAttempts = 0, attempt.lockedUntil = null
             where attempt.attemptKey = :attemptKey
            """)
    void clearFailures(@Param("attemptKey") byte[] attemptKey);

    @Query("""
            select attempt.failedAttempts from LoginAttempt attempt
             where attempt.attemptKey = :attemptKey
            """)
    Optional<Integer> readFailedAttempts(@Param("attemptKey") byte[] attemptKey);
}
