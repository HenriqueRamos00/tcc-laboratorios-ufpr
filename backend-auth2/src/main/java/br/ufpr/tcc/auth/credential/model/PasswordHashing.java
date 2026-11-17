package br.ufpr.tcc.auth.credential.model;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Como a senha de pessoa e guardada.
 *
 * O algoritmo e Argon2id, e a escolha veio de uma divergencia de contrato medida, nao de
 * moda: o consumidor cobra 72 **caracteres** no login e o BCrypt cobra 72 **bytes**, e
 * entre os dois mora a faixa de senha acentuada que o consumidor aceita e que o BCrypt
 * recusa com excecao. Argon2id nao tem teto de byte, entao o unico limite que sobra e o do
 * proprio consumidor.
 *
 * O sal e sorteado por senha pelo proprio codificador e viaja dentro da forma guardada,
 * junto com o custo. Por isso nao existe coluna de sal: sal nao e segredo, e o que ele
 * impede e que duas pessoas com a mesma senha tenham a mesma forma guardada e que uma
 * tabela pronta sirva para as duas.
 *
 * Os parametros sao o perfil de defesa equivalente da OWASP, que troca memoria por
 * passagens mantendo o mesmo produto. O minimo mais citado, com 19 MiB por verificacao,
 * nao cabe aqui: com o teto de memoria declarado no compose, a conta da fatia de carga deu
 * entre nove e doze logins simultaneos antes de a maquina virtual morrer por falta de
 * memoria, e o reinicio automatico transformaria pico de login em laco de reinicio.
 */
public final class PasswordHashing {

    /** Memoria por verificacao, em kibibytes. */
    private static final int MEMORY_IN_KIBIBYTES = 7168;

    /** Passagens sobre a memoria. Sobe quando a memoria desce, para manter o mesmo custo. */
    private static final int ITERATIONS = 5;

    /** Uma linha de execucao por verificacao: o paralelismo aqui disputaria com as requisicoes. */
    private static final int PARALLELISM = 1;

    private static final int SALT_LENGTH_IN_BYTES = 16;

    private static final int HASH_LENGTH_IN_BYTES = 32;

    /** Prefixo que a forma guardada carrega, e que permite trocar algoritmo sem migrar dado. */
    private static final String ALGORITHM_ID = "argon2";

    private PasswordHashing() {
    }

    /**
     * O codificador unico da aplicacao.
     *
     * O mapa tem uma entrada so. Nao se usa a fabrica pronta do Spring Security porque ela
     * registra tambem codificadores que este servico nunca pode alcancar nem por acidente
     * de dado, entre eles o que nao cifra nada.
     *
     * Forma guardada sem prefixo estoura em vez de ser tratada como texto plano: hash sem
     * prefixo e defeito de dado, e falhar alto e melhor que autenticar errado em silencio.
     */
    public static PasswordEncoder encoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                SALT_LENGTH_IN_BYTES, HASH_LENGTH_IN_BYTES, PARALLELISM, MEMORY_IN_KIBIBYTES, ITERATIONS);
        return new DelegatingPasswordEncoder(ALGORITHM_ID, Map.of(ALGORITHM_ID, argon2));
    }
}
