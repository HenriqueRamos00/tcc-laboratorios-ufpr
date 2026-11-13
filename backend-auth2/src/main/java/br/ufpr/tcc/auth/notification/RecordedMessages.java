package br.ufpr.tcc.auth.notification;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guarda as ultimas mensagens emitidas, para que seja possivel exercitar o fluxo de
 * confirmacao sem servidor de e-mail.
 *
 * Ela e **desligada por padrao** e so grava quando a propriedade a liga, porque guardar o
 * segredo de confirmacao em memoria e exatamente o tipo de conveniencia que vaza. Ligada,
 * ela guarda no maximo as ultimas mensagens, para nao crescer sem teto.
 *
 * Ligada com servidor de e-mail configurado, ela recusa a subida. Guardar o segredo em memoria
 * so se justifica onde nao existe envio de verdade; onde existe, isso nao e conveniencia, e uma
 * copia do token de recuperacao de senha de quem usa o Portal esperando na heap de um processo
 * que ninguem lembra que esta com a chave virada. A recusa e na subida porque propriedade ligada
 * por engano nao aparece em teste: aparece meses depois, num despejo de memoria.
 */
@Component
public class RecordedMessages {

    private static final int MAXIMUM_RETAINED = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordedMessages.class);

    private final boolean enabled;

    private final Map<String, String> lastTokenByRecipient =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAXIMUM_RETAINED;
                }
            };

    RecordedMessages(@Value("${auth.notification.record-messages:false}") boolean enabled,
            @Value("${spring.mail.host:}") String mailServerHost) {
        if (enabled && !mailServerHost.isBlank()) {
            throw new IllegalStateException("auth.notification.record-messages nao pode ficar"
                    + " ligada com spring.mail.host definido: desligue uma das duas");
        }
        if (enabled) {
            LOGGER.warn("auth.notification.record-messages esta ligada: o token de cada"
                    + " mensagem fica em memoria ate a aplicacao parar");
        }
        this.enabled = enabled;
    }

    synchronized void record(OutboundMessage message) {
        if (enabled) {
            lastTokenByRecipient.put(message.recipient(), message.token());
        }
    }

    public synchronized String lastTokenFor(String recipient) {
        return lastTokenByRecipient.get(recipient);
    }

    public synchronized void clear() {
        lastTokenByRecipient.clear();
    }
}
