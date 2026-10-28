package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Leitura e gravacao de conta de cliente e das credenciais de uso unico dela. */
@Component
class ClientAccountRegistry {

    private final JdbcClient jdbcClient;

    ClientAccountRegistry(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    boolean emailAlreadyUsedIn(long applicationId, String email) {
        return jdbcClient.sql("SELECT COUNT(*) FROM CLIENT_ACCOUNT"
                        + " WHERE APPLICATION_ID = :applicationId"
                        + " AND EMAIL_NORMALIZED = LOWER(:email)")
                .param("applicationId", applicationId)
                .param("email", withoutSurroundingSpace(email))
                .query(Long.class).single() > 0L;
    }

    void insert(long id, long applicationId, String name, String email, String passwordHash) {
        jdbcClient.sql("INSERT INTO CLIENT_ACCOUNT (ID, APPLICATION_ID, NAME, EMAIL,"
                        + " EMAIL_NORMALIZED, PASSWORD_HASH, EMAIL_CONFIRMED, ACTIVE,"
                        + " CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, :name, :email, LOWER(:email),"
                        + " :hash, 0, 1, :now, :now)")
                .param("id", id).param("applicationId", applicationId).param("name", name)
                .param("email", withoutSurroundingSpace(email))
                .param("hash", passwordHash).param("now", OffsetDateTime.now()).update();
    }

    void issueConfirmation(long id, long accountId, ConfirmationToken token,
            OutboundMessage.Purpose purpose, Duration lifetime) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient.sql("INSERT INTO ACCOUNT_CONFIRMATION (ID, CLIENT_ACCOUNT_ID, PURPOSE,"
                        + " TOKEN_DIGEST, CREATED_AT, EXPIRES_AT)"
                        + " VALUES (:id, :accountId, :purpose, :digest, :now, :expiresAt)")
                .param("id", id).param("accountId", accountId).param("purpose", purpose.name())
                .param("digest", token.digest().asBytes()).param("now", now)
                .param("expiresAt", now.plus(lifetime)).update();
    }

    /**
     * Consome a credencial de uso unico e devolve a conta dela.
     *
     * A marcacao de uso e feita no proprio comando, com a condicao de ainda nao ter sido
     * usada dentro do WHERE: o numero de linhas afetadas vira a decisao, e duas chamadas
     * simultaneas com o mesmo link nao conseguem as duas achar que ganharam.
     */
    /**
     * A aplicacao do chamador entra no WHERE, e nao numa comparacao depois.
     *
     * O link pertence a uma conta, e a conta pertence a uma aplicacao. Sem esta condicao a
     * rota de confirmacao era o unico lugar do servico que nao isolava por aplicacao: medido,
     * a aplicacao B confirmava conta da aplicacao A apresentando o link dela. Filtrar na
     * consulta em vez de comparar em memoria mantem o vazamento impossivel por construcao,
     * do mesmo jeito que na busca de conta.
     */
    Optional<Long> consume(long applicationId, ConfirmationToken token,
            OutboundMessage.Purpose purpose) {
        Long accountId = jdbcClient.sql("SELECT confirmation.CLIENT_ACCOUNT_ID"
                        + " FROM ACCOUNT_CONFIRMATION confirmation"
                        + " JOIN CLIENT_ACCOUNT account"
                        + " ON account.ID = confirmation.CLIENT_ACCOUNT_ID"
                        + " WHERE confirmation.TOKEN_DIGEST = :digest"
                        + " AND confirmation.PURPOSE = :purpose"
                        + " AND confirmation.USED_AT IS NULL"
                        + " AND confirmation.EXPIRES_AT > SYSTIMESTAMP"
                        + " AND account.APPLICATION_ID = :applicationId")
                .param("digest", token.digest().asBytes()).param("purpose", purpose.name())
                .param("applicationId", applicationId)
                .query(Long.class).optional().orElse(null);
        if (accountId == null) {
            return Optional.empty();
        }
        int consumed = jdbcClient.sql("UPDATE ACCOUNT_CONFIRMATION SET USED_AT = SYSTIMESTAMP"
                        + " WHERE TOKEN_DIGEST = :digest AND USED_AT IS NULL")
                .param("digest", token.digest().asBytes()).update();
        return consumed == 1 ? Optional.of(accountId) : Optional.empty();
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
        return jdbcClient.sql("SELECT ID, EMAIL FROM CLIENT_ACCOUNT"
                        + " WHERE APPLICATION_ID = :applicationId"
                        + " AND EMAIL_NORMALIZED = LOWER(:email)")
                .param("applicationId", applicationId)
                .param("email", withoutSurroundingSpace(email))
                .query((resultSet, rowNumber) -> new AccountToNotify(resultSet.getLong("ID"),
                        resultSet.getString("EMAIL")))
                .optional();
    }

    void updatePasswordHash(long accountId, String passwordHash) {
        jdbcClient.sql("UPDATE CLIENT_ACCOUNT SET PASSWORD_HASH = :hash, UPDATED_AT = :now"
                        + " WHERE ID = :id")
                .param("id", accountId).param("hash", passwordHash)
                .param("now", OffsetDateTime.now()).update();
    }

    /**
     * Derruba as credenciais de uso unico que ainda nao foram usadas.
     *
     * Trocar a senha invalida os links pendentes porque recuperacao existe exatamente para
     * o caso de a conta estar comprometida: deixar o link anterior vivo devolveria o acesso
     * a quem pediu primeiro.
     */
    /**
     * Mata os links pendentes de um proposito so.
     *
     * A recuperacao usa isto ao emitir um link novo, porque a regra diz que o anterior morre
     * quando o novo nasce. Restringir ao proposito importa: um pedido de recuperacao nao pode
     * derrubar a confirmacao de cadastro que a pessoa ainda nao usou.
     */
    void invalidatePendingConfirmations(long accountId, OutboundMessage.Purpose purpose) {
        jdbcClient.sql("UPDATE ACCOUNT_CONFIRMATION SET USED_AT = SYSTIMESTAMP"
                        + " WHERE CLIENT_ACCOUNT_ID = :accountId AND USED_AT IS NULL"
                        + " AND PURPOSE = :purpose")
                .param("accountId", accountId).param("purpose", purpose.name()).update();
    }

    void invalidatePendingConfirmations(long accountId) {
        jdbcClient.sql("UPDATE ACCOUNT_CONFIRMATION SET USED_AT = SYSTIMESTAMP"
                        + " WHERE CLIENT_ACCOUNT_ID = :accountId AND USED_AT IS NULL")
                .param("accountId", accountId).update();
    }

    void markEmailConfirmed(long accountId) {
        jdbcClient.sql("UPDATE CLIENT_ACCOUNT SET EMAIL_CONFIRMED = 1, UPDATED_AT = :now"
                        + " WHERE ID = :id")
                .param("id", accountId).param("now", OffsetDateTime.now()).update();
    }

    /** Projecao de leitura de uma conta. O hash da senha nao aparece aqui. */
    record AccountView(String id, String name, String email, String role, boolean active,
            String serviceId, boolean emailConfirmed) {
    }

    private static final String UPDATE_ACCOUNT = "UPDATE CLIENT_ACCOUNT SET NAME = :name,"
            + " ACTIVE = :active, UPDATED_AT = :now WHERE ID = :id";

    /**
     * As credenciais de uso unico saem primeiro, porque a chave estrangeira existe justamente
     * para impedir que sobrem apontando para o nada, e elas tambem filtram pela aplicacao,
     * pela conta a que pertencem.
     */
    private static final String DELETE_CONFIRMATIONS_OF = "DELETE FROM ACCOUNT_CONFIRMATION"
            + " WHERE CLIENT_ACCOUNT_ID IN (SELECT ID FROM CLIENT_ACCOUNT WHERE ID = :id";

    private static final String DELETE_ACCOUNT = "DELETE FROM CLIENT_ACCOUNT WHERE ID = :id";

    private static final String WITHIN_APPLICATION = " AND APPLICATION_ID = :applicationId";

    private static final String SELECT_ACCOUNT = "SELECT ID, NAME, EMAIL, ACTIVE,"
            + " EMAIL_CONFIRMED, APPLICATION_ID FROM CLIENT_ACCOUNT";

    private static AccountView toView(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new AccountView(Long.toString(resultSet.getLong("ID")), resultSet.getString("NAME"),
                resultSet.getString("EMAIL"), "CLIENTE", resultSet.getInt("ACTIVE") == 1,
                Long.toString(resultSet.getLong("APPLICATION_ID")),
                resultSet.getInt("EMAIL_CONFIRMED") == 1);
    }

    /**
     * Lista contas dentro do alcance de quem chamou. Alcance vazio e administrador, que
     * atravessa aplicacoes; qualquer outro caso leva o filtro na consulta, e nunca na
     * memoria depois.
     */
    java.util.List<AccountView> list(java.util.Optional<Long> applicationScope) {
        return applicationScope
                .map(applicationId -> jdbcClient
                        .sql(SELECT_ACCOUNT + " WHERE APPLICATION_ID = :applicationId"
                                + " ORDER BY CREATED_AT DESC")
                        .param("applicationId", applicationId)
                        .query((resultSet, rowNumber) -> toView(resultSet)).list())
                .orElseGet(() -> jdbcClient.sql(SELECT_ACCOUNT + " ORDER BY CREATED_AT DESC")
                        .query((resultSet, rowNumber) -> toView(resultSet)).list());
    }

    Optional<AccountView> findWithinScope(java.util.Optional<Long> applicationScope, long accountId) {
        return applicationScope
                .map(applicationId -> jdbcClient
                        .sql(SELECT_ACCOUNT + " WHERE ID = :id AND APPLICATION_ID = :applicationId")
                        .param("id", accountId).param("applicationId", applicationId)
                        .query((resultSet, rowNumber) -> toView(resultSet)).optional())
                .orElseGet(() -> jdbcClient.sql(SELECT_ACCOUNT + " WHERE ID = :id")
                        .param("id", accountId)
                        .query((resultSet, rowNumber) -> toView(resultSet)).optional());
    }

    /**
     * O alcance de quem chamou entra tambem na gravacao, e nao apenas na leitura anterior.
     *
     * Sem ele o isolamento entre aplicacoes vive inteiro na consulta que roda antes: trocar
     * aquela consulta por uma sem escopo passaria a permitir que a aplicacao A renomeasse e
     * desativasse conta de cliente da aplicacao B, e nenhum teste cairia. Desativar conta
     * alheia e negacao de servico contra o cliente de outra aplicacao do Lactec, entao a
     * condicao e repetida de proposito. Alcance vazio e administrador, que atravessa
     * aplicacoes.
     */
    void update(java.util.Optional<Long> applicationScope, long accountId, String name,
            boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        applicationScope.ifPresentOrElse(
                applicationId -> jdbcClient.sql(UPDATE_ACCOUNT
                                + " AND APPLICATION_ID = :applicationId")
                        .param("id", accountId).param("name", name)
                        .param("active", active ? 1 : 0).param("now", now)
                        .param("applicationId", applicationId).update(),
                () -> jdbcClient.sql(UPDATE_ACCOUNT)
                        .param("id", accountId).param("name", name)
                        .param("active", active ? 1 : 0).param("now", now).update());
    }

    /**
     * Remove a conta e as credenciais de uso unico dela, dentro do alcance de quem chamou,
     * pelo mesmo motivo que a alteracao: o isolamento entre aplicacoes nao pode viver inteiro
     * na consulta que roda antes da gravacao.
     */
    void delete(java.util.Optional<Long> applicationScope, long accountId) {
        applicationScope.ifPresentOrElse(
                applicationId -> {
                    jdbcClient.sql(DELETE_CONFIRMATIONS_OF + WITHIN_APPLICATION + ")")
                            .param("id", accountId).param("applicationId", applicationId).update();
                    jdbcClient.sql(DELETE_ACCOUNT + WITHIN_APPLICATION)
                            .param("id", accountId).param("applicationId", applicationId).update();
                },
                () -> {
                    jdbcClient.sql(DELETE_CONFIRMATIONS_OF + ")").param("id", accountId).update();
                    jdbcClient.sql(DELETE_ACCOUNT).param("id", accountId).update();
                });
    }

    /**
     * Apenas o espaco das pontas. Quem passa para minuscula e o banco, com a funcao LOWER,
     * e isso nao e preferencia de estilo.
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
