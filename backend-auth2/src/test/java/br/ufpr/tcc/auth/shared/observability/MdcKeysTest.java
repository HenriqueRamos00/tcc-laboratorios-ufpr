package br.ufpr.tcc.auth.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * A lista de permissao do MDC existe para que dado pessoal nao chegue ao log por uma chave
 * que ninguem revisou. Enquanto ela era apenas declarada, um MDC.put novo passava por fora
 * dela sem que nada reclamasse.
 */
class MdcKeysTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("chave fora da lista nao chega ao MDC, e a recusa e visivel")
    void keyOutsideTheListIsRefused() {
        assertThatThrownBy(() -> MdcKeys.publish("email", "pessoa@exemplo.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");

        assertThat(MDC.get("email")).as("nada foi publicado").isNull();
    }

    @Test
    @DisplayName("a chave da lista e publicada")
    void listedKeyIsPublished() {
        MdcKeys.publish(MdcKeys.CORRELATION_ID, "requisicao-de-teste-01");

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isEqualTo("requisicao-de-teste-01");
    }
}
