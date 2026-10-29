package br.ufpr.tcc.auth.account;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prova que o comando de gravacao de conta leva a aplicacao de quem chamou, e nao apenas a
 * consulta que roda antes dele.
 *
 * Esta classe existe por uma mutacao que sobreviveu. Apagar a condicao de aplicacao do
 * UPDATE e do DELETE deixava a suite inteira verde, e o motivo nao e descuido de quem
 * escreveu os casos: a rota de alteracao consulta com escopo antes de gravar, encontra nada
 * quando a conta e de outra aplicacao, e responde 404 sem nunca chegar ao comando. Teste que
 * entra pela rota **nao pode** acusar essa perda, porque o caminho que ele percorre para
 * antes.
 *
 * Ou seja, a condicao no comando e defesa em profundidade, e defesa em profundidade sem teste
 * proprio e afirmacao em Javadoc. O que ela protege e concreto: trocar aquela consulta previa
 * por uma sem escopo, num refatoramento futuro, passaria a permitir que a aplicacao A
 * renomeasse e desativasse conta de cliente da aplicacao B, e desativar conta alheia e
 * negacao de servico contra o cliente de outra aplicacao do Lactec.
 *
 * Os dois casos de recusa vem com controle positivo ao lado, porque recusa que acontece pelo
 * motivo errado passa igual: sem o controle, uma conta que simplesmente nao existisse
 * satisfaria as duas afirmacoes.
 *
 * A transacao e aberta pelo modelo porque a gravacao e comando de conjunto, que exige
 * transacao ativa, e em producao quem a abre e o caso de uso.
 */
class AccountWriteScopeIntegrationTest extends OracleIntegrationTestBase {

    private static final AtomicLong NEXT = new AtomicLong(997_000L);

    private static final String ORIGINAL_NAME = "Nome original";

    @Autowired
    private ClientAccountRegistry registry;

    @Autowired
    private DatabaseProbe database;

    @Autowired
    private TransactionTemplate transactions;

    private long owningApplication;
    private long otherApplication;
    private long accountId;

    @BeforeEach
    void seedTwoApplicationsAndOneAccount() {
        owningApplication = NEXT.incrementAndGet();
        otherApplication = NEXT.incrementAndGet();
        accountId = NEXT.incrementAndGet();
        insertApplication(owningApplication);
        insertApplication(otherApplication);
        database.statement("INSERT INTO CLIENT_ACCOUNT (ID, APPLICATION_ID, NAME, EMAIL,"
                        + " EMAIL_NORMALIZED, PASSWORD_HASH, EMAIL_CONFIRMED, ACTIVE,"
                        + " CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, :name, :email, LOWER(:email),"
                        + " 'hash-de-teste', 1, 1, :now, :now)")
                .with("id", accountId).with("applicationId", owningApplication)
                .with("name", ORIGINAL_NAME)
                .with("email", "escopo" + accountId + "@exemplo.com")
                .with("now", OffsetDateTime.now()).run();
    }

    @Test
    @DisplayName("alteracao pedida por outra aplicacao nao toca na conta")
    void updateFromAnotherApplicationLeavesTheAccountAlone() {
        transactions.executeWithoutResult(status -> registry.update(
                Optional.of(otherApplication), accountId, "Nome invadido", false));

        assertThat(storedName()).isEqualTo(ORIGINAL_NAME);
        assertThat(storedSituation()).as("desativar conta alheia e negacao de servico")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("alteracao pedida pela aplicacao dona muda a conta")
    void updateFromTheOwningApplicationChangesTheAccount() {
        transactions.executeWithoutResult(status -> registry.update(
                Optional.of(owningApplication), accountId, "Nome novo", false));

        assertThat(storedName()).isEqualTo("Nome novo");
        assertThat(storedSituation()).isZero();
    }

    @Test
    @DisplayName("exclusao pedida por outra aplicacao nao remove a conta")
    void deleteFromAnotherApplicationLeavesTheAccountAlone() {
        transactions.executeWithoutResult(
                status -> registry.delete(Optional.of(otherApplication), accountId));

        assertThat(storedRows()).isEqualTo(1L);
    }

    @Test
    @DisplayName("exclusao pedida pela aplicacao dona remove a conta")
    void deleteFromTheOwningApplicationRemovesTheAccount() {
        transactions.executeWithoutResult(
                status -> registry.delete(Optional.of(owningApplication), accountId));

        assertThat(storedRows()).isZero();
    }

    private void insertApplication(long identifier) {
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT,"
                        + " UPDATED_AT) VALUES (:id, :code, :code, TRUE, :now, :now)")
                .with("id", identifier).with("code", "escopo-de-gravacao-" + identifier)
                .with("now", OffsetDateTime.now()).run();
    }

    private String storedName() {
        return database.statement("SELECT NAME FROM CLIENT_ACCOUNT WHERE ID = :id")
                .with("id", accountId).value(String.class);
    }

    private int storedSituation() {
        return database.statement("SELECT ACTIVE FROM CLIENT_ACCOUNT WHERE ID = :id")
                .with("id", accountId).value(Integer.class);
    }

    private long storedRows() {
        return database.statement("SELECT COUNT(*) FROM CLIENT_ACCOUNT WHERE ID = :id")
                .with("id", accountId).count();
    }
}
