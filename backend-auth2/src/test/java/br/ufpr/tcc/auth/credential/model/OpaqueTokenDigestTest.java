package br.ufpr.tcc.auth.credential.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpaqueTokenDigestTest {

    private final OpaqueTokenDigest digest = ServiceToken.generate().digest();

    @Test
    @DisplayName("o digest tem 64 caracteres hexadecimais")
    void digestIsSixtyFourHexadecimalCharacters() {
        assertThat(digest.asHexadecimal()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("o digest gravado volta do hexadecimal e confere com o original")
    void storedDigestSurvivesTheRoundTrip() {
        assertThat(OpaqueTokenDigest.fromHexadecimal(digest.asHexadecimal()).matches(digest)).isTrue();
    }

    @Test
    @DisplayName("digest de tokens diferentes nao confere")
    void digestOfAnotherTokenDoesNotMatch() {
        assertThat(digest.matches(ServiceToken.generate().digest())).isFalse();
    }

    @Test
    @DisplayName("comparar com nulo nao confere e nao estoura")
    void comparingWithNullDoesNotMatch() {
        assertThat(digest.matches(null)).isFalse();
    }

    // Comprimento invalido aqui e dado nosso corrompido, nao entrada de chamador,
    // entao nao vira falha de autenticacao.
    @Test
    @DisplayName("hexadecimal com comprimento errado e defeito de dado, nao de credencial")
    void storedDigestWithWrongLengthIsADataDefect() {
        for (String wrong : new String[] {"a".repeat(62), "a".repeat(66), "", null}) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> OpaqueTokenDigest.fromHexadecimal(wrong));
        }
    }

    @Test
    @DisplayName("alterar os bytes devolvidos nao altera o digest guardado")
    void mutatingTheReturnedBytesDoesNotChangeTheDigest() {
        byte[] exported = digest.asBytes();
        exported[0] = (byte) (exported[0] + 1);

        assertThat(digest.asBytes()).isNotEqualTo(exported);
    }
}
