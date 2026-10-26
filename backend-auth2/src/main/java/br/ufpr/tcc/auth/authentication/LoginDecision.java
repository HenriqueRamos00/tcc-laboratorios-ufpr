package br.ufpr.tcc.auth.authentication;

/**
 * O que a camada web precisa saber sobre uma tentativa de login, e nada alem.
 *
 * Duas perguntas: entrou, e por quanto tempo esta bloqueado. Os cinco motivos de recusa
 * nao aparecem aqui de proposito, porque um tipo que os carregue convida quem escreve o
 * controlador a dar resposta diferente para cada um.
 */
public record LoginDecision(Object identity, long retryAfterSeconds) {

    public boolean authenticated() {
        return identity != null;
    }

    public boolean locked() {
        return retryAfterSeconds > 0;
    }
}
