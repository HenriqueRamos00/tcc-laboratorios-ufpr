package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * Quem cadastra, altera e exclui e o administrador, pelo portal administrativo. A aplicacao
 * em que a conta nasce e as empresas que ela representa chegam no pedido, e nao na
 * credencial, porque a credencial administrativa atravessa aplicacoes. Por isso as duas sao
 * conferidas aqui: aplicacao inexistente, aplicacao desativada e empresa inexistente sao
 * desfechos proprios, e nao erro de servidor.
 *
 * O 409 de e-mail repetido e aceitavel, e isso merece justificativa: ele so e visivel para
 * quem ja pode listar todas as contas na rota ao lado. Nao existe rota de autocadastro
 * anonimo neste servico; se um dia existir, aquele 409 vira oraculo de existencia e tem de
 * virar 202.
 *
 * Os caminhos que disputam uma restricao unica, ou que precisam desfazer o que ja gravaram,
 * abrem a transacao pelo modelo e nao pela anotacao, e o motivo e de semantica e nao de
 * gosto. A recusa da restricao chega como excecao de integridade, e com a anotacao o
 * tratamento dela ficaria **dentro** da transacao: o provedor ja marcou aquela transacao
 * para desfazer, entao devolver um desfecho normal dali faria o commit falhar com erro de
 * servidor em cima de um caso que o contrato descreve como conflito. Com o modelo, a
 * transacao e desfeita antes de a excecao sair do execute, e quem decide o desfecho esta de
 * fora dela.
 */
@Service
public class AccountLifecycle {

    /** Desfecho do cadastro, com a conta criada quando houve criacao. */
    public record Registration(Outcome outcome, AccountSummary account) {

        public enum Outcome {
            CREATED, PASSWORD_REJECTED, EMAIL_TAKEN, SERVICE_NOT_FOUND, SERVICE_INACTIVE,
            COMPANY_NOT_FOUND
        }

        static Registration refused(Outcome outcome) {
            return new Registration(outcome, null);
        }
    }

    /** Desfecho da alteracao, com a conta como ficou quando houve alteracao. */
    public record Change(Outcome outcome, AccountSummary account) {

        public enum Outcome { CHANGED, NOT_FOUND, COMPANY_NOT_FOUND }

        static Change refused(Outcome outcome) {
            return new Change(outcome, null);
        }
    }

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
     * Cadastra a conta na aplicacao informada, ja vinculada as empresas informadas.
     *
     * A verificacao de senha e calculada antes de a transacao abrir. Ela custa memoria e
     * tempo por desenho, e dentro da transacao ela reteria uma conexao do pool por todo esse
     * tempo.
     *
     * A lista de empresas perde as repeticoes antes de ser gravada: o mesmo identificador
     * duas vezes e o mesmo vinculo, e mandado ao banco assim ele faria a contagem de
     * vinculos criados divergir do que foi pedido.
     */
    public Registration register(long applicationId, String name, String email, String password,
            List<Long> companyIds) {
        if (!PasswordPolicy.violationsOf(password).isEmpty()) {
            return Registration.refused(Registration.Outcome.PASSWORD_REJECTED);
        }
        String passwordHash = passwordEncoder.encode(password);
        ConfirmationToken token = ConfirmationToken.generate();
        Set<Long> companies = new LinkedHashSet<>(companyIds);
        try {
            return transactions.execute(status -> {
                Registration registration =
                        open(applicationId, name, email, passwordHash, token, companies);
                if (registration.outcome() != Registration.Outcome.CREATED) {
                    status.setRollbackOnly();
                }
                return registration;
            });
        } catch (DataIntegrityViolationException addressAlreadyTaken) {
            return Registration.refused(Registration.Outcome.EMAIL_TAKEN);
        }
    }

    /**
     * A consulta de e-mail repetido nao substitui a restricao unica do banco: entre
     * consultar e gravar existe janela, e dois cadastros simultaneos do mesmo endereco passam
     * os dois pela consulta. Quem recusa o segundo e a restricao.
     *
     * Os vinculos com empresa sao gravados antes de a credencial de confirmacao ser emitida
     * e de a mensagem ser anotada para envio: empresa inexistente desfaz o cadastro, e
     * cadastro desfeito nao pode ter deixado mensagem a caminho.
     */
    private Registration open(long applicationId, String name, String email, String passwordHash,
            ConfirmationToken token, Set<Long> companyIds) {
        Optional<Boolean> applicationActive = registry.applicationSituation(applicationId);
        if (applicationActive.isEmpty()) {
            return Registration.refused(Registration.Outcome.SERVICE_NOT_FOUND);
        }
        if (!applicationActive.get()) {
            return Registration.refused(Registration.Outcome.SERVICE_INACTIVE);
        }
        if (registry.emailAlreadyUsedIn(applicationId, email)) {
            return Registration.refused(Registration.Outcome.EMAIL_TAKEN);
        }
        long accountId = registry.insert(applicationId, name, email, passwordHash);
        if (!registry.replaceCompanies(accountId, companyIds)) {
            return Registration.refused(Registration.Outcome.COMPANY_NOT_FOUND);
        }
        registry.issueConfirmation(accountId, token,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, confirmationLifetime);
        messageDelivery.deliver(new OutboundMessage(email,
                OutboundMessage.Purpose.EMAIL_CONFIRMATION, token.clearValue()));
        auditTrail.clientAccountChanged("created", Long.toString(applicationId),
                Long.toString(accountId));
        return registry.findWithinScope(Optional.empty(), accountId)
                .map(created -> new Registration(Registration.Outcome.CREATED,
                        summaryOf(created)))
                .orElseThrow(() -> new IllegalStateException(
                        "Conta recem gravada nao foi encontrada na mesma transacao"));
    }

    /** As empresas que a conta representa, na forma que o consumidor do login ja conhece. */
    public record Authorizations(List<String> companyCnpjs) {
    }

    /** O que as rotas de cadastro e de leitura devolvem sobre uma conta. */
    public record AccountSummary(String id, String name, String email, String role, boolean active,
            String serviceId, boolean emailConfirmed, List<String> companyIds,
            Authorizations authorizations) {
    }

    private static AccountSummary summaryOf(ClientAccountRegistry.AccountView view) {
        return new AccountSummary(view.id(), view.name(), view.email(), view.role(), view.active(),
                view.serviceId(), view.emailConfirmed(), view.companyIds(),
                new Authorizations(view.companyCnpjs()));
    }

    public List<AccountSummary> list(Optional<Long> applicationScope) {
        return registry.list(applicationScope).stream().map(AccountLifecycle::summaryOf).toList();
    }

    public Optional<AccountSummary> find(Optional<Long> applicationScope, String accountId) {
        return identifierOf(accountId)
                .flatMap(id -> registry.findWithinScope(applicationScope, id))
                .map(AccountLifecycle::summaryOf);
    }

    /**
     * Altera nome, situacao e a lista inteira de empresas que a conta representa.
     *
     * A transacao e aberta pelo modelo porque empresa inexistente precisa desfazer o que ja
     * foi gravado: os vinculos anteriores sao removidos antes de os novos entrarem, e
     * devolver recusa sem desfazer deixaria a conta sem empresa nenhuma.
     *
     * A resposta e montada com o que foi pedido, e nao relendo a conta: a alteracao e um
     * comando proprio, que nao passa pela entidade ja carregada nesta transacao, entao a
     * releitura devolveria o nome e a situacao de antes.
     */
    public Change update(Optional<Long> applicationScope, String accountId, String name,
            boolean active, List<Long> companyIds) {
        Optional<Long> parsed = identifierOf(accountId);
        if (parsed.isEmpty()) {
            return Change.refused(Change.Outcome.NOT_FOUND);
        }
        long id = parsed.get();
        Set<Long> companies = new LinkedHashSet<>(companyIds);
        return transactions.execute(status -> {
            Optional<ClientAccountRegistry.AccountView> stored =
                    registry.findWithinScope(applicationScope, id);
            if (stored.isEmpty()) {
                return Change.refused(Change.Outcome.NOT_FOUND);
            }
            ClientAccountRegistry.AccountView found = stored.get();
            registry.update(applicationScope, id, name, active);
            if (!registry.replaceCompanies(id, companies)) {
                status.setRollbackOnly();
                return Change.refused(Change.Outcome.COMPANY_NOT_FOUND);
            }
            auditTrail.clientAccountChanged("changed", found.serviceId(), found.id());
            ClientAccountRegistry.CompanyLinks linked = registry.companiesOf(id);
            return new Change(Change.Outcome.CHANGED,
                    summaryOf(new ClientAccountRegistry.AccountView(found.id(), name,
                            found.email(), found.role(), active, found.serviceId(),
                            found.emailConfirmed(), linked.companyIds(),
                            linked.companyCnpjs())));
        });
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
