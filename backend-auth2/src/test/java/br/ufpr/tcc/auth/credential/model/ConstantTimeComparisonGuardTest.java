package br.ufpr.tcc.auth.credential.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guarda estrutural, e nao teste de comportamento. Esta distincao e o motivo da classe
 * existir, entao ela esta escrita aqui em vez de ficar subentendida.
 *
 * Trocar MessageDigest.isEqual por Arrays.equals devolve exatamente o mesmo booleano para
 * toda entrada possivel: o que muda e o tempo de resposta, que entrega ao atacante quantos
 * bytes ele ja acertou. Medido neste modulo: com a troca aplicada, a suite inteira
 * continuou verde, ou seja, nenhum teste de comportamento protege o requisito.
 *
 * Medir tempo dentro da JVM, com compilacao adaptativa e coletor de lixo no meio, produz
 * teste intermitente que um dia derruba a suite por motivo nenhum. Entre admitir que o
 * requisito nao tem protecao e proteger por leitura do proprio fonte, escolhemos a
 * segunda, declarada pelo que e.
 *
 * Limites, ditos antes que a banca pergunte: ela e sensivel a reformatacao do metodo e nao
 * enxerga comparacao de tempo variavel escrita em outra classe. Ela cobre a mutacao
 * nomeada e nada alem disso.
 */
class ConstantTimeComparisonGuardTest {

    private static final Path DIGEST_SOURCE =
            Path.of("src/main/java/br/ufpr/tcc/auth/credential/model/OpaqueTokenDigest.java");

    @Test
    @DisplayName("a comparacao de digest usa MessageDigest.isEqual e nao Arrays.equals")
    void digestComparisonUsesConstantTimeComparison() throws IOException {
        String source = Files.readString(DIGEST_SOURCE, StandardCharsets.UTF_8);
        int beginning = source.indexOf("public boolean matches(");
        String comparison = source.substring(beginning, source.indexOf("\n    }", beginning));

        assertThat(comparison).contains("MessageDigest.isEqual").doesNotContain("Arrays.equals");
    }
}
