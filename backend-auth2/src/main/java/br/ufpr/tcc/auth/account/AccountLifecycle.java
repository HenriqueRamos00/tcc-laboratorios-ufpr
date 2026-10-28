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
import org.springframework.dao.DuplicateKeyException;
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
    private final Duration recoveryLifetime;

    AccountLifecycle(ClientAccountRegistry registry, PasswordEncoder passwordEncoder,
            IdentifierFactory identifiers, MessageDelivery messageDelivery,
            AuditTrail auditTrail,
            @Value("${auth.confirmation.lifetime}") Duration confirmationLifetime,
            @Value("${auth.recovery.lifetime}") Duration recoveryLifetime) {
        this.registry = registry;
        this.passwordEncoder = passwordEncoder;
        this.identifiers = identifiers;
        this.messageDelivery = messageDelivery;
        this.auditTrail = auditTrail;
        this.confirmationLifetime = confirmationLifetime;
        this.recoveryLifetime = recoveryLifetime;
    }

    /**
     * O identificador de aplicacao do corpo e **pedido**, nao identidade. Quando ele
     * contradiz a credencial, a resposta e recusa e nao um campo ignorado em silencio,
     * porque ignorar ensina ao chamador que o campo existe e e aceito.
     *
     * A consulta de endereco repetido nao substitui a restricao unica do banco: entre
     * consultar e gravar existe janela, e dois cadastros simultaneos do mesmo endereco
     * passam os dois pela consulta. Quem recusa o segundo e a restricao, e sem traduzir essa
     * recusa o desfecho seria erro de servidor no lugar do conflito que a matriz manda.
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
        try {
            registry.insert(accountId, caller.applicationId(), name, email,
                    passwordEncoder.encode(password));
        } catch (DuplicateKeyException addressAlreadyTaken) {
            return RegistrationOutcome.EMAIL_TAKEN;
        }
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
                    registry.update(applicationScope, id, name, active);
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
                    registry.delete(applicationScope, id);
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
     * O pedido novo mata o link anterior, e o prazo e o da recuperacao, nao o da confirmacao
     * de cadastro: confirmacao pode esperar o dia inteiro, porque quem nao confirma nao entra,
     * e recuperacao e o contrario, ela abre a conta.
     *
     * O endereco que recebe a mensagem e o **cadastrado**, e nao o que veio no pedido: a busca
     * casa pela forma minuscula que o banco calcula, entao grafias diferentes encontram a
     * mesma conta e usar a grafia submetida mandaria o link para um endereco que nao e o dela.
     *
     * O envio ja sai do caminho da resposta, e por este motivo: o tempo a mais do endereco
     * cadastrado entregaria o cadastro mesmo com a resposta identica. A porta de envio apenas
     * anota a intencao dentro desta transacao, e quem fala com o servidor roda depois do
     * commit, noutra thread.
     *
     * Invalidar antes de inserir nao garante um unico link pendente, e isso foi medido: com
     * quatro pedidos simultaneos para a mesma conta ficaram quatro links de recuperacao
     * validos, porque duas transacoes nao se veem inserir. Quem garante o invariante agora e
     * o indice unico parcial de V006, e perder essa corrida nao e falha: significa que outro
     * pedido identico ja emitiu o link e ja vai mandar a mensagem, entao este desiste em
     * silencio e a resposta continua sendo a mesma de sempre.
     */
    @Transactional
    public void requestPasswordRecovery(ApplicationCaller caller, String email) {
        registry.findToNotifyByApplicationAndEmail(caller.applicationId(), email)
                .ifPresent(account -> {
                    registry.invalidatePendingConfirmations(account.id(),
                            OutboundMessage.Purpose.PASSWORD_RECOVERY);
                    ConfirmationToken token = ConfirmationToken.generate();
                    try {
                        registry.issueConfirmation(identifiers.nextIdentifier(), account.id(),
                                token, OutboundMessage.Purpose.PASSWORD_RECOVERY,
                                recoveryLifetime);
                    } catch (DuplicateKeyException anotherRequestWonTheRace) {
                        return;
                    }
                    messageDelivery.deliver(new OutboundMessage(account.registeredEmail(),
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
    public boolean resetPassword(ApplicationCaller caller, String presentedToken,
            String newPassword) {
        if (!PasswordPolicy.violationsOf(newPassword).isEmpty()) {
            return false;
        }
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> registry.consume(caller.applicationId(), token,
                        OutboundMessage.Purpose.PASSWORD_RECOVERY))
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
     * Link desconhecido, usado, vencido, de proposito errado e **de conta de outra
     * aplicacao** respondem igual, porque distinguir contaria se o endereco existe, em que
     * estado ele esta e em qual aplicacao.
     */
    @Transactional
    public boolean confirmEmail(ApplicationCaller caller, String presentedToken) {
        return ConfirmationToken.fromPresentedValue(presentedToken)
                .flatMap(token -> registry.consume(caller.applicationId(), token,
                        OutboundMessage.Purpose.EMAIL_CONFIRMATION))
                .map(accountId -> {
                    registry.markEmailConfirmed(accountId);
                    auditTrail.clientCredentialChanged("email_confirmed",
                            Long.toString(accountId));
                    return true;
                })
                .orElse(false);
    }
}
