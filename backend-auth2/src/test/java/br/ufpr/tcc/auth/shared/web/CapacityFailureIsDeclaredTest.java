package br.ufpr.tcc.auth.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Falta de capacidade responde um desfecho declarado, e nao erro de servidor.
 *
 * Medido numa rajada contra a aplicacao no ar: com o pool de conexao cheio, uma requisicao
 * esperou alem do tempo limite e saiu como 500, status que a matriz de codigos nao tem. O
 * processo nao caiu e as outras 399 responderam certo, mas 500 diz ao consumidor que o
 * servico quebrou, quando o que aconteceu foi fila cheia por um instante.
 *
 * O 503 com Retry-After diz a coisa certa: tente de novo em seguida. Ele e desfecho novo no
 * contrato, e por isso entra documentado em `docs/consumir-a-api.md`.
 */
class CapacityFailureIsDeclaredTest {

    private final InvalidRequestAdvice advice = new InvalidRequestAdvice(new ProblemDetailWriter());

    @Test
    @DisplayName("tempo limite de conexao responde 503 com Retry-After, e nao 500")
    void connectionTimeoutAnswersServiceUnavailable() {
        ResponseEntity<ProblemDetail> response = advice.onCapacityExhausted(
                new DataAccessResourceFailureException("pool cheio",
                        new SQLTransientConnectionException("request timed out")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .as("o consumidor le o tempo de espera em segundos inteiros")
                .isNotNull().matches("[1-9][0-9]*");
    }

    /**
     * Banco fora de alcance e indisponibilidade, e nao defeito do pedido.
     *
     * Medido na aplicacao no ar, com o contentor do banco pausado: a rota de entrada
     * administrativa respondia 500 no formato padrao do framework, com o caminho pedido
     * ecoado no corpo, sem Retry-After e com uma pilha inteira em nivel de erro no log a cada
     * requisicao. Falta de capacidade momentanea ja respondia 503; banco inacessivel nao
     * respondia, porque a excecao e outra: a de capacidade nasce do pool cheio, e esta nasce
     * de nao haver conexao para abrir transacao.
     */
    @Test
    @DisplayName("banco fora de alcance responde 503 com Retry-After, e nao 500")
    void databaseOutOfReachAnswersServiceUnavailable() {
        ResponseEntity<ProblemDetail> response = advice.onDatabaseOutOfReach(
                new CannotCreateTransactionException("could not open connection",
                        new SQLRecoverableException("ORA-17008: Closed connection")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isNotNull().matches("[1-9][0-9]*");
        assertThat(String.valueOf(response.getBody()))
                .as("o corpo nao conta qual banco nem qual codigo de erro")
                .doesNotContain("ORA-").doesNotContain("connection");
    }

    /**
     * O corpo nao pode contar o que faltou. Nome de pool, contagem de conexao e texto da
     * falha original dizem ao chamador o tamanho da instalacao, e nada disso o ajuda.
     */
    @Test
    @DisplayName("o corpo do 503 nao descreve a infraestrutura")
    void theBodyDoesNotDescribeTheInfrastructure() {
        ResponseEntity<ProblemDetail> response = advice.onCapacityExhausted(
                new DataAccessResourceFailureException("HikariPool-1 total=10 active=10"));

        assertThat(String.valueOf(response.getBody()))
                .doesNotContain("Hikari").doesNotContain("active=").doesNotContain("total=");
    }
}
