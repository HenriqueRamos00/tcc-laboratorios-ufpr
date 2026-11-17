package br.ufpr.tcc.auth.authentication;

/**
 * Desfecho de uma tentativa de login do ponto de vista do contador.
 *
 * O prazo de espera vem junto porque a resposta de bloqueio precisa dele no cabecalho, e
 * o consumidor le o valor em segundos inteiros: a outra forma que a norma permite deixaria
 * o campo vazio no cliente e a tela mostraria falha de infraestrutura.
 */
public record LoginAttemptOutcome(boolean locked, long retryAfterSeconds) {

    static LoginAttemptOutcome allowed() {
        return new LoginAttemptOutcome(false, 0L);
    }
}
