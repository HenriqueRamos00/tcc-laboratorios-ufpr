package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.identifier.IdentifierFactory;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de conta de cliente e confirmacao do endereco.
 *
 * A conta nasce **sem poder entrar**: o login exige endereco confirmado, e so o link
 * enviado libera. Cadastrar sem isso seria cadastrar uma conta inutil, e confirmar sem
 * cadastrar nao existe.
 *
 * Quem pode cadastrar e quem apresenta a credencial da propria aplicacao. O 409 de e-mail
 * repetido e aceitavel aqui, e isso merece justificativa: ele so e visivel para quem ja
 * tem o token da aplicacao, que e segredo de servidor e nao credencial de usuario final.
 * Nao existe rota de autocadastro anonimo neste servico; se um dia existir, aquele 409
 * vira oraculo de existencia e tem de virar 202.
 */
@Service
public class AccountLifecycle {

    public enum RegistrationOutcome { CREATED, PASSWORD_REJECTED, SCOPE_REJECTED, EMAIL_TAKEN }

    private final ClientAccountRegistry registry;
    private final PasswordEncoder passwordEncoder;
    private final IdentifierFactory identifiers;
    private final MessageDelivery messageDelivery;
    private final AuditTrail auditTrail;
    private final Duration confirmationLifetime;

    AccountLifecycle(ClientAccountRegistry registry, PasswordEncoder passwordEncoder,
            IdentifierFactory identifiers, MessageDelivery messageDelivery,
            AuditTrail auditTrail,
            @Value("${auth.confirmation.lifetime:PT24H}") Duration confirmationLifetime) {
        this.registry = registry;
        this.passwordEncoder = passwordEncoder;
        this.identifiers = identifiers;
        this.messageDelivery = messageDelivery;
        this.auditTrail = auditTrail;
        this.confirmationLifetime = confirmationLifetime;
    }

    /**
     * O identificador de aplicacao do corpo e **pedido**, nao identidade. Quando ele
     * contradiz a credencial, a resposta e recusa e nao um campo ignorado em silencio,
     * porque ignorar ensina ao chamador que o campo existe e e aceito.
     */
    @Transactional
    public RegistrationOutcome register(ApplicationCaller caller, String requestedApplicationId,
            String name, String email, String password) {
        if (requestedApplicationId != null
                && !requestedApplicationId.equals(Long.toString(caller.applicationId()))) {
            return RegistrationOutcome.SCOPE_REJECTED;
        }
        if (!PasswordPolicy.violationsOf(password).isEmpty()) {
            return RegistrationOutcome.PASSWORD_REJECTED;
        }
        if (registry.emailAlreadyUsedIn(caller.applicationId(), email)) {
            return RegistrationOutcome.EMAIL_TAKEN;
        }
        long accountId = identifiers.nextIdentifier();
        registry.insert(accountId, caller.applicationId(), name, email,
                passwordEncoder.encode(password));
        ConfirmationToken token = ConfirmationToken.generate();
        registry.issueConfirmation(identifiers.nextIdentifier(), accountId, token,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, confirmationLifetime);
        messageDelivery.deliver(new OutboundMessage(email,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, token.clearValue()));
        auditTrail.clientAccountChanged("created", Long.toString(caller.applicationId()),
                Long.toString(accountId));
        return RegistrationOutcome.CREATED;
    }

    /** O que as rotas de leitura devolvem sobre uma conta. */
    public record AccountSummary(String id, String name, String email, String role, boolean active,
            String serviceId, boolean emailConfirmed) {
    }

    private static AccountSummary summaryOf(ClientAccountRegistry.AccountView view) {
        return new AccountSummary(view.id(), view.name(), view.email(), view.role(), view.active(),
                view.serviceId(), view.emailConfirmed());
    }

    public java.util.List<AccountSummary> list(java.util.Optional<Long> applicationScope) {
        return registry.list(applicationScope).stream().map(AccountLifecycle::summaryOf).toList();
    }

    public java.util.Optional<AccountSummary> find(java.util.Optional<Long> applicationScope,
            String accountId) {
        return identifierOf(accountId)
                .flatMap(id -> registry.findWithinScope(applicationScope, id))
                .map(AccountLifecycle::summaryOf);
    }

    @Transactional
    public java.util.Optional<AccountSummary> update(java.util.Optional<Long> applicationScope,
            String accountId, String name, boolean active) {
        return identifierOf(accountId)
                .flatMap(id -> registry.findWithinScope(applicationScope, id).map(found -> {
                    registry.update(id, name, active);
                    auditTrail.clientAccountChanged("changed", found.serviceId(), found.id());
                    return summaryOf(new ClientAccountRegistry.AccountView(found.id(), name,
                            found.email(), found.role(), active, found.serviceId(),
                            found.emailConfirmed()));
                }));
    }

    @Transactional
    public boolean delete(java.util.Optional<Long> applicationScope, String accountId) {
        return identifierOf(accountId)
                .flatMap(id -> registry.findWithinScope(applicationScope, id).map(found -> {
                    registry.delete(id);
                    auditTrail.clientAccountChanged("removed", found.serviceId(), found.id());
                    return true;
                }))
                .orElse(false);
    }

    /** Identificador que nao e numero e identificador que nao existe sao o mesmo caso. */
    private static java.util.Optional<Long> identifierOf(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Pedido de recuperacao de senha.
     *
     * Devolve sempre a mesma coisa, exista ou nao o endereco, e por isso nao devolve nada.
     * Endereco que nao existe simplesmente nao gera link, e nenhuma mensagem sai.
     *
     * O envio ja sai do caminho da resposta, e por este motivo: o tempo a mais do endereco
     * cadastrado entregaria o cadastro mesmo com a resposta identica. A porta de envio apenas
     * anota a intencao dentro desta transacao, e quem fala com o servidor roda depois do
     * commit, noutra thread.
     */
    @Transactional
    public void requestPasswordRecovery(ApplicationCaller caller, String email) {
        registry.findIdByApplicationAndEmail(caller.applicationId(), email).ifPresent(accountId -> {
            ConfirmationToken token = ConfirmationToken.generate();
            registry.issueConfirmation(identifiers.nextIdentifier(), accountId, token,
                    OutboundMessage.Purpose.PASSWORD_RECOVERY, confirmationLifetime);
            messageDelivery.deliver(new OutboundMessage(email,
                    OutboundMessage.Purpose.PASSWORD_RECOVERY, token.clearValue()));
        });
    }

    /**
     * Troca a senha a partir do link enviado.
     *
     * A politica e cobrada antes de consumir o link: senha fraca nao pode queimar a unica
     * chance de quem perdeu o acesso.
     *
     * O proposito faz parte do que e verificado. Um link de confirmacao de cadastro chega a
     * quem acabou de se inscrever e nao pode trocar senha de ninguem.
     */
    @Transactional
    public boolean resetPassword(String presentedToken, String newPassword) {
        if (!PasswordPolicy.violationsOf(newPassword).isEmpty()) {
            return false;
        }
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> registry.consume(token, OutboundMessage.Purpose.PASSWORD_RECOVERY))
                .map(accountId -> {
                    registry.updatePasswordHash(accountId, passwordEncoder.encode(newPassword));
                    registry.invalidatePendingConfirmations(accountId);
                    auditTrail.clientCredentialChanged("password_reset",
                            Long.toString(accountId));
                    return true;
                })
                .orElse(false);
    }

    /**
     * Link desconhecido, usado, vencido e de proposito errado respondem igual, porque
     * distinguir contaria se o endereco existe e em que estado ele esta.
     */
    @Transactional
    public boolean confirmEmail(String presentedToken) {
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> registry.consume(token, OutboundMessage.Purpose.EMAIL_CONFIRMATION))
                .map(accountId -> {
                    registry.markEmailConfirmed(accountId);
                    auditTrail.clientCredentialChanged("email_confirmed",
                            Long.toString(accountId));
                    return true;
                })
                .orElse(false);
    }
}
