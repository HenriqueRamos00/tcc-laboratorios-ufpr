package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.AccountConstraintViolation;
import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envia o link de recuperacao de senha, sem revelar se o e-mail tem conta.
 *
 * Rota: POST /api/auth/recoveries, credencial de aplicacao, que responde 202 nos dois casos.
 * Pedido novo substitui o link anterior. O indice UX_ACCOUNT_CONFIRMATION_PENDING garante um
 * pendente por conta e finalidade; somente esse conflito concorrente e ignorado, depois do
 * rollback, e por isso a transacao e aberta pelo modelo.
 */
@Service
public class RequestPasswordRecoveryUseCase {

    private final ClientAccountStore store;
    private final MessageDelivery messageDelivery;
    private final TransactionTemplate transactions;
    private final Duration recoveryLifetime;

    RequestPasswordRecoveryUseCase(ClientAccountStore store, MessageDelivery messageDelivery,
            TransactionTemplate transactions,
            @Value("${auth.recovery.lifetime}") Duration recoveryLifetime) {
        this.store = store;
        this.messageDelivery = messageDelivery;
        this.transactions = transactions;
        this.recoveryLifetime = recoveryLifetime;
    }

    public void execute(ApplicationCaller caller, String email) {
        ConfirmationToken token = ConfirmationToken.generate();
        try {
            transactions.executeWithoutResult(status -> issueRecovery(caller, email, token));
        } catch (DataIntegrityViolationException failure) {
            if (!AccountConstraintViolation.matches(failure, "UX_ACCOUNT_CONFIRMATION_PENDING")) {
                throw failure;
            }
        }
    }

    /** Envia o token ao endereco cadastrado, encontrado pela comparacao de e-mail no banco. */
    private void issueRecovery(ApplicationCaller caller, String email, ConfirmationToken token) {
        store.findToNotifyByApplicationAndEmail(caller.applicationId(), email)
                .ifPresent(account -> {
                    store.invalidatePendingTokens(account.id(),
                            OutboundMessage.Purpose.PASSWORD_RECOVERY);
                    store.issueToken(account.id(), token,
                            OutboundMessage.Purpose.PASSWORD_RECOVERY, recoveryLifetime);
                    messageDelivery.deliver(new OutboundMessage(account.registeredEmail(),
                            OutboundMessage.Purpose.PASSWORD_RECOVERY, token.clearValue()));
                });
    }
}
