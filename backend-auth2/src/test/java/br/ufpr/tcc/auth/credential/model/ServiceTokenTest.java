package br.ufpr.tcc.auth.credential.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceTokenTest {

    /**
     * Copia literal da expressao que o consumidor valida na inicializacao dele, em
     * Portal.Api/Configuration/PortalSourceRegistration.cs:65. Ela e digitada de novo aqui,
     * e nao importada do codigo de producao, de proposito: teste que compartilha a
     * constante com o codigo acompanha a mutacao dela e deixa de ser contrato.
     */
    private static final String CONSUMER_CONTRACT_FORMAT = "^svc_[A-Za-z0-9_-]{43}$";

    private static final String VALID_SECRET = "ab_CD-efGH0123456789abcdefghijklmnopqrstuvw";

    @Test
    @DisplayName("o token emitido casa a expressao que o Portal valida ao subir")
    void generatedTokenMatchesConsumerContractFormat() {
        for (int emission = 0; emission < 500; emission++) {
            assertThat(ServiceToken.generate().clearValue()).matches(CONSUMER_CONTRACT_FORMAT);
        }
    }

    @Test
    @DisplayName("o token emitido tem 43 caracteres depois do prefixo")
    void generatedTokenCarriesFortyThreeCharactersAfterThePrefix() {
        assertThat(ServiceToken.generate().clearValue()).startsWith("svc_").hasSize(47);
    }

    @Test
    @DisplayName("o alfabeto emitido nunca usa os caracteres do base64 classico")
    void generatedTokenNeverCarriesCharacterOutsideTheUrlSafeAlphabet() {
        assertThat(ServiceToken.generate().clearValue())
                .doesNotContain("+").doesNotContain("/").doesNotContain("=");
    }

    @Test
    @DisplayName("emissoes em sequencia nao repetem o token")
    void consecutiveGenerationsProduceDifferentTokens() {
        Set<String> emitted = new HashSet<>();
        for (int emission = 0; emission < 500; emission++) {
            emitted.add(ServiceToken.generate().clearValue());
        }

        assertThat(emitted).hasSize(500);
    }

    @Test
    @DisplayName("token com 43 caracteres depois do prefixo e aceito")
    void acceptsPresentedValueWithFortyThreeCharactersAfterThePrefix() {
        assertThat(ServiceToken.fromPresentedValue("svc_" + VALID_SECRET).clearValue())
                .isEqualTo("svc_" + VALID_SECRET);
    }

    // Valor-limite no comprimento (42 e 44), particionamento no conjunto de
    // caracteres, e error guessing em prefixo, caixa e espaco nas pontas.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "svc_",
            "svc_ab_CD-efGH0123456789abcdefghijklmnopqrstuv",
            "svc_ab_CD-efGH0123456789abcdefghijklmnopqrstuvwx",
            "svc_ab+CD-efGH0123456789abcdefghijklmnopqrstuvw",
            "svc_ab/CD-efGH0123456789abcdefghijklmnopqrstuvw",
            "svc_ab=CD-efGH0123456789abcdefghijklmnopqrstuvw",
            "svc_ab.CD-efGH0123456789abcdefghijklmnopqrstuvw",
            "SVC_ab_CD-efGH0123456789abcdefghijklmnopqrstuvw",
            "svc-ab_CD-efGH0123456789abcdefghijklmnopqrstuvw",
            "ab_CD-efGH0123456789abcdefghijklmnopqrstuvw",
            " svc_ab_CD-efGH0123456789abcdefghijklmnopqrstuvw",
            "svc_ab_CD-efGH0123456789abcdefghijklmnopqrstuvw "
    })
    @DisplayName("token fora do formato de contrato e recusado como falha de autenticacao")
    void rejectsPresentedValueOutsideTheContractFormat(String presentedValue) {
        assertThatExceptionOfType(MalformedServiceTokenException.class)
                .isThrownBy(() -> ServiceToken.fromPresentedValue(presentedValue));
    }

    @Test
    @DisplayName("a recusa nao carrega o valor recusado na mensagem")
    void malformedTokenFailureDoesNotCarryTheRejectedValue() {
        String presentedValue = "svc_" + VALID_SECRET + "x";

        assertThatExceptionOfType(MalformedServiceTokenException.class)
                .isThrownBy(() -> ServiceToken.fromPresentedValue(presentedValue))
                .withMessageNotContaining(VALID_SECRET);
    }

    @Test
    @DisplayName("toString nao mostra o segredo")
    void toStringDoesNotCarryTheSecret() {
        ServiceToken token = ServiceToken.generate();

        assertThat(token.toString()).doesNotContain(token.clearValue().substring(4));
    }

    @Test
    @DisplayName("o mesmo token produz sempre o mesmo digest")
    void sameTokenAlwaysProducesTheSameDigest() {
        String clearValue = ServiceToken.generate().clearValue();

        assertThat(ServiceToken.fromPresentedValue(clearValue).digest())
                .isEqualTo(ServiceToken.fromPresentedValue(clearValue).digest());
    }

    @Test
    @DisplayName("tokens diferentes produzem digests diferentes")
    void differentTokensProduceDifferentDigests() {
        assertThat(ServiceToken.generate().digest())
                .isNotEqualTo(ServiceToken.generate().digest());
    }

    @Test
    @DisplayName("o digest nao carrega o token em claro")
    void digestDoesNotCarryTheTokenInClear() {
        ServiceToken token = ServiceToken.generate();

        assertThat(token.digest().asHexadecimal()).doesNotContain(token.clearValue().substring(4));
    }
}
