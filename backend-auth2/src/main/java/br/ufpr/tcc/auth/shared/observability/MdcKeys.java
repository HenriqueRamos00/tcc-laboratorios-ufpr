package br.ufpr.tcc.auth.shared.observability;

import java.util.Set;
import org.slf4j.MDC;

/**
 * Chaves que podem aparecer no MDC e, por consequencia, no JSON do log.
 *
 * A lista e de permissao, nao de proibicao, e ela e aplicada e nao apenas declarada: quem
 * publica passa por publish, que recusa chave fora da lista. Assim um MDC.put novo nao leva
 * senha, token, nome ou e-mail para o log sem passar por revisao, porque incluir a chave aqui
 * e uma linha de patch que alguem le.
 */
public final class MdcKeys {

    /**
     * Identificador de correlacao da requisicao. O nome e correlation_id e nao traceId de
     * proposito: traceId e spanId sao as chaves do padrao de correlacao do Spring Boot e
     * do Micrometer, e usar uma delas faria o log afirmar que existe um trace distribuido,
     * que aqui nao existe.
     */
    public static final String CORRELATION_ID = "correlation_id";

    /**
     * Endereco de onde a requisicao chegou, que a auditoria exige em cada evento.
     *
     * E o endereco do par do soquete, e nao o de cabecalho encaminhado, porque cabecalho e
     * escrito por quem chama. Atras de proxy, este valor e o do proxy, e o endereco do
     * usuario final nao existe nesta aplicacao: o porque esta em docs/restricao-por-regiao.md.
     */
    public static final String ORIGIN = "origin";

    private static final Set<String> ALLOWED = Set.of(CORRELATION_ID, ORIGIN);

    private MdcKeys() {
    }

    public static Set<String> allowed() {
        return ALLOWED;
    }

    /**
     * Publica a chave no MDC, recusando o que nao esta na lista.
     *
     * A recusa e excecao e nao registro silencioso, porque chave nova no log e decisao de
     * revisao: quem precisa de uma acrescenta na lista, e quem tentar contrabandear dado
     * pessoal por uma chave inventada descobre no primeiro teste.
     */
    public static void publish(String key, String value) {
        if (!ALLOWED.contains(key)) {
            throw new IllegalArgumentException(
                    "Chave fora da lista de permissao do MDC: " + key);
        }
        MDC.put(key, value);
    }
}
