package br.ufpr.tcc.auth.credential.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Casos projetados por valor-limite no minimo de oito pontos de codigo, particionamento de
 * equivalencia nas quatro condicoes exercitadas dos dois lados, e error guessing em
 * espaco, acento, emoji, caractere fora do plano basico, vazio e nulo.
 *
 * As senhas com caractere fora do plano basico sao montadas por appendCodePoint em vez de
 * literal, porque a forma composta e a decomposta do acento sao identicas na tela e o caso
 * de teste viraria adivinhacao para quem revisa.
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("sete pontos de codigo violam apenas o tamanho minimo")
    void sevenCodePointsViolateOnlyTheMinimumLength() {
        assertThat(PasswordPolicy.violationsOf("Senha12"))
                .containsExactly(PasswordRequirement.MINIMUM_LENGTH);
    }

    @Test
    @DisplayName("oito pontos de codigo cumprem todos os requisitos")
    void eightCodePointsSatisfyEveryRequirement() {
        assertThat(PasswordPolicy.violationsOf("Senha123")).isEmpty();
    }

    @Test
    @DisplayName("nove pontos de codigo cumprem todos os requisitos")
    void nineCodePointsSatisfyEveryRequirement() {
        assertThat(PasswordPolicy.violationsOf("Senha1234")).isEmpty();
    }

    @Test
    @DisplayName("maiuscula e minuscula sem digito violam apenas o digito")
    void uppercaseAndLowercaseWithoutDigitViolateOnlyTheDigit() {
        assertThat(PasswordPolicy.violationsOf("Senhaboa"))
                .containsExactly(PasswordRequirement.DIGIT);
    }

    @Test
    @DisplayName("maiuscula e digito sem minuscula violam apenas a minuscula")
    void uppercaseAndDigitWithoutLowercaseViolateOnlyTheLowercase() {
        assertThat(PasswordPolicy.violationsOf("SENHA123"))
                .containsExactly(PasswordRequirement.LOWERCASE_LETTER);
    }

    @Test
    @DisplayName("minuscula e digito sem maiuscula violam apenas a maiuscula")
    void lowercaseAndDigitWithoutUppercaseViolateOnlyTheUppercase() {
        assertThat(PasswordPolicy.violationsOf("senha123"))
                .containsExactly(PasswordRequirement.UPPERCASE_LETTER);
    }

    @Test
    @DisplayName("senha sem letra e sem digito viola os tres requisitos de caractere")
    void passwordWithoutLetterAndWithoutDigitViolatesTheThreeCharacterRequirements() {
        assertThat(PasswordPolicy.violationsOf("!!!!!!!!"))
                .containsExactlyInAnyOrder(PasswordRequirement.UPPERCASE_LETTER,
                        PasswordRequirement.LOWERCASE_LETTER, PasswordRequirement.DIGIT);
    }

    @Test
    @DisplayName("senha curta sem letra e sem digito viola todos os requisitos")
    void shortPasswordWithoutLetterAndWithoutDigitViolatesEveryRequirement() {
        assertThat(PasswordPolicy.violationsOf("!!!"))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PasswordRequirement.class));
    }

    @Test
    @DisplayName("senha so de espaco tem tamanho suficiente e viola os de caractere")
    void passwordMadeOnlyOfSpacesViolatesTheThreeCharacterRequirements() {
        assertThat(PasswordPolicy.violationsOf("        "))
                .containsExactlyInAnyOrder(PasswordRequirement.UPPERCASE_LETTER,
                        PasswordRequirement.LOWERCASE_LETTER, PasswordRequirement.DIGIT);
    }

    // Com trim a senha cairia para sete pontos e seria recusada, entao este caso
    // e o que prova que a politica nao normaliza a entrada.
    @Test
    @DisplayName("espaco no fim conta para o tamanho porque a senha nao sofre trim")
    void trailingSpaceCountsTowardTheMinimumLength() {
        assertThat(PasswordPolicy.violationsOf("Senha12 ")).isEmpty();
    }

    @Test
    @DisplayName("espaco no meio da senha e aceito")
    void spaceInsideThePasswordIsAccepted() {
        assertThat(PasswordPolicy.violationsOf("Senha 12")).isEmpty();
    }

    @Test
    @DisplayName("senha vazia viola todos os requisitos")
    void emptyPasswordViolatesEveryRequirement() {
        assertThat(PasswordPolicy.violationsOf(""))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PasswordRequirement.class));
    }

    @Test
    @DisplayName("senha nula viola todos os requisitos em vez de estourar excecao")
    void nullPasswordViolatesEveryRequirement() {
        assertThat(PasswordPolicy.violationsOf(null))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PasswordRequirement.class));
    }

    // O emoji ocupa dois char, entao esta senha tem length() igual a oito e
    // apenas sete pontos de codigo. Contar char daria ela por boa.
    @Test
    @DisplayName("emoji conta como um ponto de codigo e nao como dois caracteres")
    void emojiCountsAsOneCodePointAndNotAsTwoCharacters() {
        String password = new StringBuilder("Abc123").appendCodePoint(0x1F600).toString();

        assertThat(PasswordPolicy.violationsOf(password))
                .containsExactly(PasswordRequirement.MINIMUM_LENGTH);
    }

    @Test
    @DisplayName("caractere fora do plano basico completa o tamanho minimo")
    void characterOutsideTheBasicPlaneCompletesTheMinimumLength() {
        String password = new StringBuilder("Abcd123").appendCodePoint(0x1D11E).toString();

        assertThat(PasswordPolicy.violationsOf(password)).isEmpty();
    }

    // A forma composta e a decomposta do mesmo texto tem contagem diferente de
    // pontos de codigo. E isso que significa nao remover acentuacao: as duas sao
    // senhas diferentes.
    @Test
    @DisplayName("forma composta e decomposta do acento sao contadas em separado")
    void composedAndDecomposedAccentsAreCountedSeparately() {
        String composed = new StringBuilder().appendCodePoint(0x00C1).append("nimo12").toString();
        String decomposed = new StringBuilder("A").appendCodePoint(0x0301).append("nimo12").toString();

        assertThat(PasswordPolicy.violationsOf(composed))
                .containsExactly(PasswordRequirement.MINIMUM_LENGTH);
        assertThat(PasswordPolicy.violationsOf(decomposed)).isEmpty();
    }

    @Test
    @DisplayName("a lista de violacoes devolvida nao pode ser alterada pelo chamador")
    void returnedViolationsCannotBeModified() {
        Set<PasswordRequirement> violations = PasswordPolicy.violationsOf("");

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(violations::clear);
    }
}
