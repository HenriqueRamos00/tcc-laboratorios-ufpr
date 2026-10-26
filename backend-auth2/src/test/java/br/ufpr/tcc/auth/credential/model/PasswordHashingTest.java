package br.ufpr.tcc.auth.credential.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Casos do algoritmo de senha. O que esta fixado aqui nao e gosto: a escolha do Argon2id
 * veio de uma divergencia de contrato medida, em que o consumidor cobra 72 caracteres e o
 * BCrypt cobra 72 bytes, e o caso da senha acentuada longa e o que prova que a divergencia
 * sumiu.
 */
class PasswordHashingTest {

    private final PasswordEncoder encoder = PasswordHashing.encoder();

    @Test
    @DisplayName("a senha guardada nao e a senha digitada")
    void storedFormIsNotTheTypedPassword() {
        assertThat(encoder.encode("Senha123")).doesNotContain("Senha123");
    }

    // Sal por senha e o que impede que duas pessoas com a mesma senha tenham a
    // mesma forma guardada, e o que torna inutil uma tabela pronta.
    @Test
    @DisplayName("a mesma senha gera formas guardadas diferentes, porque o sal e por senha")
    void samePasswordProducesDifferentStoredForms() {
        assertThat(encoder.encode("Senha123")).isNotEqualTo(encoder.encode("Senha123"));
    }

    @Test
    @DisplayName("a senha correta confere com a forma guardada dela")
    void correctPasswordMatchesItsStoredForm() {
        assertThat(encoder.matches("Senha123", encoder.encode("Senha123"))).isTrue();
    }

    @Test
    @DisplayName("senha errada nao confere")
    void wrongPasswordDoesNotMatch() {
        assertThat(encoder.matches("Senha124", encoder.encode("Senha123"))).isFalse();
    }

    // Este e o caso que decidiu o algoritmo. Quarenta caracteres acentuados
    // passam de 72 bytes em UTF-8, e o Portal cobra o limite em caracteres, entao
    // com BCrypt esta senha chegaria aqui valida e seria recusada com excecao.
    @Test
    @DisplayName("senha com mais de 72 bytes em caracteres acentuados funciona inteira")
    void accentedPasswordAboveSeventyTwoBytesWorks() {
        String password = "Çã1" + "ç".repeat(40);
        assertThat(password.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);
        assertThat(password.length()).isLessThanOrEqualTo(72);

        String stored = encoder.encode(password);

        assertThat(encoder.matches(password, stored)).isTrue();
        assertThat(encoder.matches(password.substring(0, 36), stored)).isFalse();
    }

    @Test
    @DisplayName("espaco na ponta da senha conta, porque nada e normalizado")
    void spaceAtTheEdgeOfThePasswordCounts() {
        String stored = encoder.encode("Senha123 ");

        assertThat(encoder.matches("Senha123 ", stored)).isTrue();
        assertThat(encoder.matches("Senha123", stored)).isFalse();
    }

    // O prefixo e o que permite trocar algoritmo ou custo depois sem migrar dado:
    // a forma antiga continua legivel porque ela diz como foi gerada.
    @Test
    @DisplayName("a forma guardada declara o algoritmo e os parametros de custo")
    void storedFormDeclaresTheAlgorithmAndItsCost() {
        assertThat(encoder.encode("Senha123"))
                .startsWith("{argon2}$argon2id$v=19$m=7168,t=5,p=1$");
    }

    @Test
    @DisplayName("forma guardada sem prefixo de algoritmo e recusada em vez de passar")
    void storedFormWithoutAlgorithmPrefixIsRejected() {
        assertThat(catchThrowableOfMatching()).isInstanceOf(IllegalArgumentException.class);
    }

    private Throwable catchThrowableOfMatching() {
        try {
            encoder.matches("Senha123", "$argon2id$v=19$m=7168,t=5,p=1$c2FsdA$aGFzaA");
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }
}
