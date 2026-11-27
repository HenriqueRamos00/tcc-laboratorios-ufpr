package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redefine a senha com o link de recuperacao.
 *
 * Rota: POST /api/auth/password-resets, credencial de aplicacao. A senha e validada antes de
 * consumir o link, para que senha fora da politica nao gaste o link. Invalida as recuperacoes
 * e as trocas de e-mail pendentes, preservando a confirmacao de e-mail.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ResetPasswordUseCase {

    private final ClientAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrail auditTrail;

    @Transactional
    public boolean execute(ApplicationCaller caller, String presentedToken, String newPassword) {
        if (!PasswordPolicy.violationsOf(newPassword).isEmpty()) {
            return false;
        }
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> store.consumeToken(caller.applicationId(), token,
                        OutboundMessage.Purpose.PASSWORD_RECOVERY))
                .map(accountId -> {
                    store.updatePasswordHash(accountId, passwordEncoder.encode(newPassword));
                    store.invalidatePendingTokens(accountId,
                            OutboundMessage.Purpose.PASSWORD_RECOVERY);
                    store.invalidatePendingEmailChangeTokens(accountId);
                    store.clearEmailChange(accountId);
                    auditTrail.clientCredentialChanged("password_reset",
                            Long.toString(accountId));
                    return true;
                })
                .orElse(false);
    }
}
