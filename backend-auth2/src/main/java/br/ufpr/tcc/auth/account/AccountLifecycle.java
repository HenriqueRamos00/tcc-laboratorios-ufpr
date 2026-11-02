package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
 *
 * Os dois caminhos que disputam uma restricao unica abrem a transacao pelo modelo e nao
 * pela anotacao, e o motivo e de semantica e nao de gosto. A recusa da restricao chega como
 * excecao de integridade, e com a anotacao o tratamento dela ficaria **dentro** da
 * transacao: o provedor ja marcou aquela transacao para desfazer, entao devolver um desfecho
 * normal dali faria o commit falhar com erro de servidor em cima de um caso que o contrato
 * descreve como conflito. Com o modelo, a transacao e desfeita antes de a excecao sair do
 * execute, e quem decide o desfecho esta de fora dela.
 */
@Service
public class AccountLifecycle {

    public enum RegistrationOutcome { CREATED, PASSWORD_REJECTED, SCOPE_REJECTED, EMAIL_TAKEN }

    private final ClientAccountRegistry registry;
    private final PasswordEncoder passwordEncoder;
    private final MessageDelivery messageDelivery;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;
    private final Duration confirmationLifetime;
    private final Duration recoveryLifetime;

    AccountLifecycle(ClientAccountRegistry registry, PasswordEncoder passwordEncoder,
            MessageDelivery messageDelivery, AuditTrail auditTrail,
            TransactionTemplate transactions,
            @Value("${auth.confirmation.lifetime}") Duration confirmationLifetime,
            @Value("${auth.recovery.lifetime}") Duration recoveryLifetime) {
        this.registry = registry;
        this.passwordEncoder = passwordEncoder;
        this.messageDelivery = messageDelivery;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
        this.confirmationLifetime = confirmationLifetime;
        this.recoveryLifetime = recoveryLifetime;
    }

    /**
     * O identificador de aplicacao do corpo e **pedido**, nao identidade. Quando ele
     * contradiz a credencial, a resposta e recusa e nao um campo ignorado em silencio,
     * porque ignorar ensina ao chamador que o campo existe e e aceito.
     *
     * A verificacao de senha e calculada antes de a transacao abrir. Ela custa memoria e
     * tempo por desenho, e dentro da transacao ela reteria uma conexao do pool por todo esse
     * tempo numa rota que qualquer aplicacao registrada alcanca.
     */
    public RegistrationOutcome register(ApplicationCaller caller, String requestedApplicationId,
            String name, String email, String password) {
        if (requestedApplicationId != null
                && !requestedApplicationId.equals(Long.toString(caller.applicationId()))) {
            return RegistrationOutcome.SCOPE_REJECTED;
        }
        if (!PasswordPolicy.violationsOf(password).isEmpty()) {
            return RegistrationOutcome.PASSWORD_REJECTED;
        }
        String passwordHash = passwordEncoder.encode(password);
        ConfirmationToken token = ConfirmationToken.generate();
        try {
            return transactions.execute(
                    status -> open(caller, name, email, passwordHash, token));
        } catch (DataIntegrityViolationException addressAlreadyTaken) {
            return RegistrationOutcome.EMAIL_TAKEN;
        }
    }

    /**
     * A consulta de endereco repetido nao substitui a restricao unica do banco: entre
     * consultar e gravar existe janela, e dois cadastros simultaneos do mesmo endereco
     * passam os dois pela consulta. Quem recusa o segundo e a restricao, e quem traduz a
     * recusa para conflito e o chamador deste metodo.
     *
     * A mensagem e pedida aqui dentro de proposito: a porta de envio anota a intencao na
     * transacao e quem fala com o servidor roda depois do commit. Pedida de fora, ela cairia
     * no caminho que registra o aviso e nao manda nada.
     */
    private RegistrationOutcome open(ApplicationCaller caller, String name, String email,
            String passwordHash, ConfirmationToken token) {
        if (registry.emailAlreadyUsedIn(caller.applicationId(), email)) {
            return RegistrationOutcome.EMAIL_TAKEN;
        }
        long accountId = registry.insert(caller.applicationId(), name, email, passwordHash);
        registry.issueConfirmation(accountId, token,
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

    public List<AccountSummary> list(Optional<Long> applicationScope) {
        return registry.list(applicationScope).stream().map(AccountLifecycle::summaryOf).toList();
    }

    public Optional<AccountSummary> find(Optional<Long> applicationScope, String accountId) {
        return identifierOf(accountId)
                .flatMap(id -> registry.findWithinScope(applicationScope, id))
                .map(AccountLifecycle::summaryOf);
    }

    @Transactional
    public Optional<AccountSummary> update(Optional<Long> applicationScope, String accountId,
            String name, boolean active) {
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
    public boolean delete(Optional<Long> applicationScope, String accountId) {
        return identifierOf(accountId)
                .flatMap(id -> registry.findWithinScope(applicationScope, id).map(found -> {
                    registry.delete(applicationScope, id);
                    auditTrail.clientAccountChanged("removed", found.serviceId(), found.id());
                    return true;
                }))
                .orElse(false);
    }

    /** Identificador que nao e numero e identificador que nao existe sao o mesmo caso. */
    private static Optional<Long> identifierOf(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
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
     * Invalidar antes de inserir nao garante um unico link pendente, e isso foi medido: com
     * quatro pedidos simultaneos para a mesma conta ficaram quatro links de recuperacao
     * validos, porque duas transacoes nao se veem inserir. Quem garante o invariante e o
     * indice unico parcial de V008, e perder essa corrida nao e falha: significa que outro
     * pedido identico ja emitiu o link e ja vai mandar a mensagem.
     *
     * Quem perde desiste **inteiro**, e nao apenas sem inserir. A invalidacao dos links
     * anteriores e desfeita junto, e isso corrige um efeito que o tratamento anterior tinha:
     * tratada dentro da transacao, a invalidacao do perdedor era confirmada sem o link novo
     * dele, e podia derrubar justamente o link que o vencedor acabou de emitir.
     */
    public void requestPasswordRecovery(ApplicationCaller caller, String email) {
        ConfirmationToken token = ConfirmationToken.generate();
        try {
            transactions.executeWithoutResult(status -> issueRecovery(caller, email, token));
        } catch (DataIntegrityViolationException anotherRequestWonTheRace) {
            return;
        }
    }

    /**
     * O endereco que recebe a mensagem e o **cadastrado**, e nao o que veio no pedido: a busca
     * casa pela forma minuscula que o banco calcula, entao grafias diferentes encontram a
     * mesma conta e usar a grafia submetida mandaria o link para um endereco que nao e o dela.
     */
    private void issueRecovery(ApplicationCaller caller, String email, ConfirmationToken token) {
        registry.findToNotifyByApplicationAndEmail(caller.applicationId(), email)
                .ifPresent(account -> {
                    registry.invalidatePendingConfirmations(account.id(),
                            OutboundMessage.Purpose.PASSWORD_RECOVERY);
                    registry.issueConfirmation(account.id(), token,
                            OutboundMessage.Purpose.PASSWORD_RECOVERY, recoveryLifetime);
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
