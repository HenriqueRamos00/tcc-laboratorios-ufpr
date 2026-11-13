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
class ClientAccountRegistry {

    /**
     * O perfil tem um unico valor aceito neste servico, e por isso ele e constante e nao
     * coluna. Quando existir mais de um, ele vira coluna e esta linha sai.
     */
    private static final String ONLY_ROLE = "CLIENTE";

    private final ClientAccountRepository accounts;
    private final AccountConfirmationRepository confirmations;
    private final ClientAccountCompanyRepository companyLinks;

    /** A situacao da aplicacao em que a conta vai nascer: vazio quando ela nao existe. */
    Optional<Boolean> applicationSituation(long applicationId) {
        return accounts.findApplicationSituation(applicationId);
    }

    boolean emailAlreadyUsedIn(long applicationId, String email) {
        return accounts.countWithinApplicationByEmail(applicationId,
                withoutSurroundingSpace(email)) > 0L;
    }

    /**
     * Grava a conta e devolve o identificador que o gerador atribuiu.
     *
     * A gravacao e descarregada na hora, e nao no fim da transacao, para que a recusa da
     * restricao unica aconteca aqui: adiada para o commit, ela apareceria depois de a
     * credencial de confirmacao ja ter sido emitida e da porta de envio ja ter anotado a
     * mensagem.
     */
    long insert(long applicationId, String name, String email, String passwordHash) {
        return accounts.saveAndFlush(ClientAccount.openFor(applicationId, name,
                withoutSurroundingSpace(email), passwordHash)).id();
    }

    void issueConfirmation(long accountId, ConfirmationToken token,
            OutboundMessage.Purpose purpose, Duration lifetime) {
        confirmations.saveAndFlush(
                AccountConfirmation.issuedFor(accountId, token, purpose, lifetime));
    }

    /**
     * Consome a credencial de uso unico e devolve a conta dela.
     *
     * A marcacao de uso e feita com a condicao de ainda nao ter sido usada dentro do
     * proprio comando: o numero de linhas afetadas vira a decisao, e duas chamadas
     * simultaneas com o mesmo link nao conseguem as duas achar que ganharam.
     */
    Optional<Long> consume(long applicationId, ConfirmationToken token,
            OutboundMessage.Purpose purpose) {
        return confirmations
                .findLiveAccountFor(applicationId, token.digest().asBytes(), purpose)
                .filter(accountId -> confirmations
                        .consumeByDigest(token.digest().asBytes()) == 1);
    }

    /**
     * A conta encontrada para o pedido de recuperacao, com o endereco **cadastrado**.
     *
     * O endereco vem daqui e nao do pedido porque a busca casa pela forma minuscula que o
     * banco calcula: grafias diferentes encontram a mesma conta, e mandar a mensagem para a
     * grafia submetida faria o link do titular sair para um endereco que nao e o cadastrado.
     */
    record AccountToNotify(long id, String registeredEmail) {
    }

    Optional<AccountToNotify> findToNotifyByApplicationAndEmail(long applicationId, String email) {
        return accounts
                .findWithinApplicationByEmail(applicationId, withoutSurroundingSpace(email))
                .map(account -> new AccountToNotify(account.id(), account.email()));
    }

    void updatePasswordHash(long accountId, String passwordHash) {
        accounts.findById(accountId).ifPresent(account -> account.passwordHash(passwordHash));
    }

    void invalidatePendingConfirmations(long accountId, OutboundMessage.Purpose purpose) {
        confirmations.invalidatePendingOf(accountId, purpose);
    }

    void invalidatePendingConfirmations(long accountId) {
        confirmations.invalidateEveryPendingOf(accountId);
    }

    void markEmailConfirmed(long accountId) {
        accounts.findById(accountId).ifPresent(ClientAccount::confirmEmail);
    }

    /** Projecao de leitura de uma conta. O hash da senha nao aparece aqui. */
    record AccountView(String id, String name, String email, String role, boolean active,
            String serviceId, boolean emailConfirmed, List<String> companyIds,
            List<String> companyCnpjs) {
    }

    /** As empresas que uma conta representa, como as rotas de leitura as devolvem. */
    record CompanyLinks(List<String> companyIds, List<String> companyCnpjs) {

        /**
         * As duas listas saem na mesma ordem, a do identificador da empresa, para que a
         * posicao de um identificador case com a do CNPJ dele e a resposta nao mude entre
         * chamadas.
         */
        static CompanyLinks of(List<CompanyLinkRow> links) {
            List<CompanyLinkRow> ordered = links.stream()
                    .sorted(Comparator.comparingLong(CompanyLinkRow::companyId)).toList();
            return new CompanyLinks(
                    ordered.stream().map(link -> Long.toString(link.companyId())).toList(),
                    ordered.stream().map(CompanyLinkRow::cnpj).toList());
        }
    }

    CompanyLinks companiesOf(long accountId) {
        return CompanyLinks.of(companyLinks.findOfAccount(accountId));
    }

    private static AccountView toView(ClientAccount account, List<CompanyLinkRow> links) {
        CompanyLinks companies = CompanyLinks.of(links);
        return new AccountView(Long.toString(account.id()), account.name(), account.email(),
                ONLY_ROLE, account.active(), Long.toString(account.applicationId()),
                account.emailConfirmed(), companies.companyIds(), companies.companyCnpjs());
    }

    /**
     * Lista contas dentro do alcance de quem chamou. Alcance vazio e administrador, que
     * atravessa aplicacoes; qualquer outro caso leva o filtro na consulta, e nunca na
     * memoria depois.
     *
     * Os vinculos com empresa vem numa segunda consulta, com o mesmo alcance, e nao numa
     * por conta: listar cem contas custa duas idas ao banco, e nao cento e uma.
     */
    List<AccountView> list(Optional<Long> applicationScope) {
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

    Optional<AccountView> findWithinScope(Optional<Long> applicationScope, long accountId) {
        return applicationScope
                .map(applicationId -> accounts.findByIdAndApplicationId(accountId, applicationId))
                .orElseGet(() -> accounts.findById(accountId))
                .map(account -> toView(account, companyLinks.findOfAccount(account.id())));
    }

    /**
     * Troca a lista inteira de empresas que a conta representa, e diz se todas existiam.
     *
     * A lista e substituida, e nao somada: quem altera declara o estado que quer, e lista
     * vazia desfaz todos os vinculos. Quando alguma empresa pedida nao existe, os vinculos
     * anteriores ja foram desfeitos, entao quem chama precisa desfazer a transacao.
     */
    boolean replaceCompanies(long accountId, Collection<Long> companyIds) {
        companyLinks.unlinkAllOf(accountId);
        return companyIds.isEmpty()
                || companyLinks.link(accountId, companyIds) == companyIds.size();
    }

    /**
     * O alcance de quem chamou entra tambem na gravacao, e nao apenas na leitura anterior,
     * e e por isso que a alteracao e um comando proprio em vez de mexer na entidade
     * carregada: mexer na entidade faria o UPDATE sair apenas com a chave primaria, e o
     * isolamento entre aplicacoes passaria a viver inteiro na consulta que roda antes.
     * Alcance vazio e administrador, que atravessa aplicacoes.
     */
    void update(Optional<Long> applicationScope, long accountId, String name, boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        applicationScope.ifPresentOrElse(
                applicationId -> accounts.updateWithinApplication(accountId, applicationId, name,
                        active, now),
                () -> accounts.updateAcrossApplications(accountId, name, active, now));
    }

    /**
     * Remove a conta e as credenciais de uso unico dela, dentro do alcance de quem chamou,
     * pelo mesmo motivo que a alteracao.
     */
    void delete(Optional<Long> applicationScope, long accountId) {
        applicationScope.ifPresentOrElse(
                applicationId -> {
                    confirmations.deleteWithinApplication(accountId, applicationId);
                    accounts.deleteWithinApplication(accountId, applicationId);
                },
                () -> {
                    confirmations.deleteAcrossApplications(accountId);
                    accounts.deleteAcrossApplications(accountId);
                });
    }

    /**
     * Apenas o espaco das pontas. Quem passa para minuscula e o banco, pela funcao LOWER no
     * parametro da consulta e pelo transformador de coluna da entidade, e isso nao e
     * preferencia de estilo.
     *
     * A coluna normalizada e conferida por uma restricao que compara com LOWER(EMAIL), e foi
     * medido aqui: o I maiusculo com ponto, U+0130, desce em Java para duas unidades de
     * codigo e em Oracle para outra coisa, entao calcular a minuscula nos dois lugares fazia
     * a restricao reprovar a gravacao e a rota responder erro de servidor onde o contrato
     * promete cadastro ou recusa. Com a minuscula saindo so do banco, os dois lados nao tem
     * como divergir.
     *
     * O corte de espaco alcanca apenas o que esta ate U+0020, e isto tambem foi medido neste
     * repositorio: o espaco nao quebravel U+00A0 e a largura zero U+200B sobrevivem tanto a
     * trim quanto a strip. Entao esta funcao deixa o endereco gravavel, e nao limpo.
     *
     * Nada disto alcanca senha. Senha nao passa por normalizacao nenhuma, porque cortar
     * espaco trocaria a credencial em silencio.
     */
    static String withoutSurroundingSpace(String email) {
        return email == null ? "" : email.trim();
    }
}
