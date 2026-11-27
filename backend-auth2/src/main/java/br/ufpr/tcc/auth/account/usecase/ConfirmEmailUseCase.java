package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirma o e-mail da conta com o link enviado no cadastro, liberando a entrada.
 *
 * Rota: POST /api/auth/confirmations, credencial de aplicacao. O link vale uma vez so e
 * apenas na aplicacao da conta. Link desconhecido, usado, vencido ou de finalidade
 * incorreta produz a mesma recusa.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ConfirmEmailUseCase {

    private final ClientAccountStore store;
    private final AuditTrail auditTrail;

    @Transactional
    public boolean execute(ApplicationCaller caller, String presentedToken) {
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> store.consumeToken(caller.applicationId(), token,
                        OutboundMessage.Purpose.EMAIL_CONFIRMATION))
                .map(accountId -> {
                    store.confirmEmail(accountId);
                    auditTrail.clientCredentialChanged("email_confirmed",
                            Long.toString(accountId));
                    return true;
                })
                .orElse(false);
    }
}
