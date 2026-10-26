package br.ufpr.tcc.auth.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A copia em memoria das mensagens, que existe para exercitar o fluxo de confirmacao onde nao
 * ha servidor de e-mail.
 *
 * O caso que importa e o da combinacao proibida. Guardar o segredo em memoria so se justifica
 * onde nao existe envio de verdade; onde existe, isso e uma copia do token de recuperacao de
 * senha de quem usa o Portal esperando na heap de um processo que ninguem lembra que esta com a
 * chave virada. A recusa precisa ser na subida, porque propriedade ligada por engano nao
 * aparece em teste: aparece meses depois, num despejo de memoria.
 */
class RecordedMessagesTest {

    private static final OutboundMessage MESSAGE = new OutboundMessage("pessoa@lactec.exemplo",
            OutboundMessage.Purpose.EMAIL_CONFIRMATION, "cnf_tokenDeTeste");

    @Test
    @DisplayName("ligada com servidor de e-mail configurado, ela recusa a subida")
    void enabledWithAMailServerRefusesToStart() {
        assertThatThrownBy(() -> new RecordedMessages(true, "smtp.exemplo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("record-messages");
    }

    @Test
    @DisplayName("ligada sem servidor de e-mail, ela guarda o token da ultima mensagem")
    void enabledWithoutAMailServerKeepsTheLastToken() {
        RecordedMessages recorded = new RecordedMessages(true, "");

        recorded.record(MESSAGE);

        assertThat(recorded.lastTokenFor("pessoa@lactec.exemplo")).isEqualTo("cnf_tokenDeTeste");
    }

    @Test
    @DisplayName("desligada, ela nao guarda nada")
    void disabledKeepsNothing() {
        RecordedMessages recorded = new RecordedMessages(false, "");

        recorded.record(MESSAGE);

        assertThat(recorded.lastTokenFor("pessoa@lactec.exemplo")).isNull();
    }
}
