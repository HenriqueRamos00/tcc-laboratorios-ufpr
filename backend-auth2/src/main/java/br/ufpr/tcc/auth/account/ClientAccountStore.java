package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Leitura e gravacao de conta de cliente e das credenciais de uso unico dela. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ClientAccountStore {

    /** Todas as contas deste servico usam o perfil CLIENTE. */
    private static final String ONLY_ROLE = "CLIENTE";

    private final ClientAccountRepository accounts;
    private final AccountActionTokenRepository tokens;
    private final ClientAccountCompanyRepository companyLinks;
    private final ClientEmailChangeRepository emailChanges;

    public record EmailChangeTokenOwner(long accountId, OutboundMessage.Purpose purpose) {
    }

    /** A situacao da aplicacao em que a conta vai nascer: vazio quando ela nao existe. */
    public Optional<Boolean> applicationSituation(long applicationId) {
        return accounts.findApplicationSituation(applicationId);
    }

    public boolean emailAlreadyUsedIn(long applicationId, String email) {
        return accounts.countWithinApplicationByEmail(applicationId,
                withoutSurroundingSpace(email)) > 0L;
    }

    public boolean emailUsedByAnotherIn(long applicationId, String email, long accountId) {
        return accounts.countOtherAccountsWithinApplicationByEmail(applicationId, email,
                accountId) > 0L;
    }

    public ClientEmailChange emailChange(long accountId, long applicationId) {
        return emailChanges.findById(accountId)
                .filter(change -> change.applicationId() == applicationId)
                .orElseGet(() -> ClientEmailChange.open(accountId, applicationId,
                        OffsetDateTime.now()));
    }

    public Optional<ClientEmailChange> pendingEmailChange(long accountId, long applicationId) {
        return emailChanges.findById(accountId)
                .filter(change -> change.applicationId() == applicationId && change.pending());
    }

    public Optional<EmailChangeTokenOwner> findEmailChangeTokenOwner(long applicationId,
            ConfirmationToken token) {
        byte[] digest = token.digest().asBytes();
        return tokens.findLiveAccountFor(applicationId, digest,
                        OutboundMessage.Purpose.EMAIL_CHANGE_CURRENT)
                .map(id -> new EmailChangeTokenOwner(id,
                        OutboundMessage.Purpose.EMAIL_CHANGE_CURRENT))
                .or(() -> tokens.findLiveAccountFor(applicationId, digest,
                        OutboundMessage.Purpose.EMAIL_CHANGE_NEW)
                        .map(id -> new EmailChangeTokenOwner(id,
                                OutboundMessage.Purpose.EMAIL_CHANGE_NEW)));
    }

    public boolean consumeEmailChangeToken(ConfirmationToken token,
            OutboundMessage.Purpose purpose) {
        return tokens.consumeByDigest(token.digest().asBytes(), purpose) == 1;
    }

    public void issueEmailChangeToken(long accountId, ConfirmationToken token,
            OutboundMessage.Purpose purpose, Duration lifetime) {
        issueToken(accountId, token, purpose, lifetime);
    }

    public void saveAccount(ClientAccount account) {
        accounts.saveAndFlush(account);
    }

    public void saveEmailChange(ClientEmailChange change) {
        emailChanges.saveAndFlush(change);
    }

    public void clearEmailChange(long accountId) {
        emailChanges.findById(accountId).ifPresent(ClientEmailChange::clearPending);
    }

    /** Desfaz a troca de e-mail pendente: os links dela deixam de valer e o pedido e limpo. */
    public void discardPendingEmailChange(long accountId) {
        invalidatePendingEmailChangeTokens(accountId);
        clearEmailChange(accountId);
    }

    /**
     * Insere e descarrega a gravacao para detectar a violacao de unicidade antes de emitir o
     * token.
     */
    public long insert(long applicationId, String name, String email, String passwordHash) {
        return accounts.saveAndFlush(ClientAccount.openFor(applicationId, name,
                withoutSurroundingSpace(email), passwordHash)).id();
    }

    public void issueToken(long accountId, ConfirmationToken token,
            OutboundMessage.Purpose purpose, Duration lifetime) {
        tokens.saveAndFlush(
                AccountActionToken.issuedFor(accountId, token, purpose, lifetime));
    }

    /**
     * Valida o token e marca seu uso por UPDATE condicional. Somente uma chamada concorrente pode
     * consumir o mesmo token.
     */
    public Optional<Long> consumeToken(long applicationId, ConfirmationToken token,
            OutboundMessage.Purpose purpose) {
        return tokens
                .findLiveAccountFor(applicationId, token.digest().asBytes(), purpose)
                .filter(accountId -> tokens
                        .consumeByDigest(token.digest().asBytes(), purpose) == 1);
    }

    /** Conta e endereco cadastrado que recebera a mensagem de recuperacao. */
    public record AccountToNotify(long id, String registeredEmail) {
    }

    public Optional<AccountToNotify> findToNotifyByApplicationAndEmail(long applicationId,
            String email) {
        return accounts
                .findWithinApplicationByEmail(applicationId, withoutSurroundingSpace(email))
                .map(account -> new AccountToNotify(account.id(), account.email()));
    }

    public void updatePasswordHash(long accountId, String passwordHash) {
        accounts.findById(accountId).ifPresent(account -> account.changePasswordHash(passwordHash));
    }

    public void invalidatePendingTokens(long accountId, OutboundMessage.Purpose purpose) {
        tokens.invalidatePendingOf(accountId, purpose);
    }

    public void invalidatePendingEmailChangeTokens(long accountId) {
        tokens.invalidatePendingOf(accountId, OutboundMessage.Purpose.EMAIL_CHANGE_CURRENT);
        tokens.invalidatePendingOf(accountId, OutboundMessage.Purpose.EMAIL_CHANGE_NEW);
    }

    public void confirmEmail(long accountId) {
        accounts.findById(accountId).ifPresent(ClientAccount::confirmEmail);
    }

    /** Projecao de leitura de uma conta. O hash da senha nao aparece aqui. */
    public record AccountView(String id, String name, String email, String role, boolean active,
            String serviceId, boolean emailConfirmed, List<String> companyIds,
            List<String> companyCnpjs) {
    }

    public record SelfServiceAccount(long id, long applicationId, String name, String email,
            String passwordHash, boolean active, boolean emailConfirmed, long credentialVersion,
            List<String> companyIds, List<String> companyCnpjs) {
    }

    /** As empresas que uma conta representa, como as rotas de leitura as devolvem. */
    public record CompanyLinks(List<String> companyIds, List<String> companyCnpjs) {

        /** Ordena ambas as listas por ID da empresa, mantendo a correspondencia entre ID e CNPJ. */
        static CompanyLinks of(List<CompanyLinkRow> links) {
            List<CompanyLinkRow> ordered = links.stream()
                    .sorted(Comparator.comparingLong(CompanyLinkRow::companyId)).toList();
            return new CompanyLinks(
                    ordered.stream().map(link -> Long.toString(link.companyId())).toList(),
                    ordered.stream().map(CompanyLinkRow::cnpj).toList());
        }
    }

    public CompanyLinks companiesOf(long accountId) {
        return CompanyLinks.of(companyLinks.findOfAccount(accountId));
    }

    public Optional<SelfServiceAccount> selfServiceAccount(long applicationId, long accountId,
            boolean lockForUpdate) {
        Optional<ClientAccount> found = lockForUpdate
                ? accounts.findByIdAndApplicationIdForUpdate(accountId, applicationId)
                : accounts.findByIdAndApplicationId(accountId, applicationId);
        return found.map(account -> {
            CompanyLinks links = companiesOf(account.id());
            return new SelfServiceAccount(account.id(), account.applicationId(), account.name(),
                    account.email(), account.passwordHash(), account.active(),
                    account.emailConfirmed(), account.credentialVersion(), links.companyIds(),
                    links.companyCnpjs());
        });
    }

    public Optional<ClientAccount> lockedAccount(long applicationId, long accountId) {
        return accounts.findByIdAndApplicationIdForUpdate(accountId, applicationId);
    }

    private static AccountView toView(ClientAccount account, List<CompanyLinkRow> links) {
        CompanyLinks companies = CompanyLinks.of(links);
        return new AccountView(Long.toString(account.id()), account.name(), account.email(),
                ONLY_ROLE, account.active(), Long.toString(account.applicationId()),
                account.emailConfirmed(), companies.companyIds(), companies.companyCnpjs());
    }

    /**
     * Lista contas no escopo: vazio representa administrador; preenchido filtra pela aplicacao no
     * banco. Busca os vinculos em lote para evitar uma consulta por conta.
     */
    public List<AccountView> list(Optional<Long> applicationScope) {
        List<ClientAccount> found = applicationScope
                .map(accounts::findByApplicationIdOrderByCreatedAtDesc)
                .orElseGet(accounts::findByOrderByCreatedAtDesc);
        Map<Long, List<CompanyLinkRow>> linksByAccount = applicationScope
                .map(companyLinks::findWithinApplication)
                .orElseGet(companyLinks::findAcrossApplications)
                .stream().collect(Collectors.groupingBy(CompanyLinkRow::accountId));
        return found.stream()
                .map(account -> toView(account,
                        linksByAccount.getOrDefault(account.id(), List.of())))
                .toList();
    }

    public Optional<AccountView> findWithinScope(Optional<Long> applicationScope, long accountId) {
        return applicationScope
                .map(applicationId -> accounts.findByIdAndApplicationId(accountId, applicationId))
                .orElseGet(() -> accounts.findById(accountId))
                .map(account -> toView(account, companyLinks.findOfAccount(account.id())));
    }

    /**
     * Substitui todos os vinculos. Lista vazia remove todos. Se faltar alguma empresa, o chamador
     * deve desfazer a transacao.
     */
    public boolean replaceCompanies(long accountId, Collection<Long> companyIds) {
        companyLinks.unlinkAllOf(accountId);
        return companyIds.isEmpty()
                || companyLinks.link(accountId, companyIds) == companyIds.size();
    }

    /**
     * Aplica o escopo tambem no UPDATE para manter o isolamento entre aplicacoes. Escopo vazio
     * permite a operacao administrativa.
     */
    public void update(Optional<Long> applicationScope, long accountId, String name,
            boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        applicationScope.ifPresentOrElse(
                applicationId -> accounts.updateWithinApplication(accountId, applicationId, name,
                        active, now),
                () -> accounts.updateAcrossApplications(accountId, name, active, now));
    }

    /** Exclui tokens e conta dentro do escopo autorizado. */
    public void delete(Optional<Long> applicationScope, long accountId) {
        applicationScope.ifPresentOrElse(
                applicationId -> {
                    emailChanges.deleteById(accountId);
                    tokens.deleteWithinApplication(accountId, applicationId);
                    accounts.deleteWithinApplication(accountId, applicationId);
                },
                () -> {
                    emailChanges.deleteById(accountId);
                    tokens.deleteAcrossApplications(accountId);
                    accounts.deleteAcrossApplications(accountId);
                });
    }

    /**
     * Remove espacos das pontas com trim; LOWER e calculado apenas no banco. Senhas nao passam por
     * esta normalizacao.
     */
    public static String withoutSurroundingSpace(String email) {
        return email == null ? "" : email.trim();
    }
}
