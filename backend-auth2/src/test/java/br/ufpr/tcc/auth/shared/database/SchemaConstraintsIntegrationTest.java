package br.ufpr.tcc.auth.shared.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prova as regras que moram no banco, contra um Oracle de verdade. Elas nao podem viver so
 * na aplicacao: duas requisicoes simultaneas passam as duas pela consulta de existencia
 * antes de qualquer gravacao, e so a restricao transforma isso em erro em vez de dois
 * cadastros.
 *
 * As insercoes vao por parametro nomeado. Nome de tabela entra por constante, porque
 * identificador nao pode ser parametro, e nenhum valor e concatenado.
 */
@Transactional
class SchemaConstraintsIntegrationTest extends OracleIntegrationTestBase {

    @Autowired
    private JdbcClient jdbcClient;

    private void insertApplication(long id, String code) {
        jdbcClient.sql("INSERT INTO " + SCHEMA_PREFIX + "APPLICATION"
                        + " (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :name, TRUE, :now, :now)")
                .param("id", id).param("code", code).param("name", "Aplicacao " + code)
                .param("now", OffsetDateTime.now()).update();
    }

    private void insertCompany(long id, long applicationId, String cnpj) {
        jdbcClient.sql("INSERT INTO " + SCHEMA_PREFIX + "COMPANY"
                        + " (ID, APPLICATION_ID, NAME, CNPJ, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, :name, :cnpj, :now, :now)")
                .param("id", id).param("applicationId", applicationId)
                .param("name", "Empresa " + id).param("cnpj", cnpj)
                .param("now", OffsetDateTime.now()).update();
    }

    private void insertAccount(long id, long applicationId, Long companyId, String name, String email) {
        jdbcClient.sql("INSERT INTO " + SCHEMA_PREFIX + "CLIENT_ACCOUNT"
                        + " (ID, APPLICATION_ID, COMPANY_ID, NAME, EMAIL, EMAIL_NORMALIZED,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, :companyId, :name, :email, :normalized,"
                        + " '{argon2}forma-guardada-de-teste', :now, :now)")
                .param("id", id).param("applicationId", applicationId).param("companyId", companyId)
                .param("name", name).param("email", email)
                .param("normalized", email.toLowerCase(java.util.Locale.ROOT))
                .param("now", OffsetDateTime.now()).update();
    }

    // Unicidade global faria o cadastro da aplicacao B recusar um e-mail por causa da
    // aplicacao A, o que entrega a existencia da conta de uma para a outra.
    @Test
    @DisplayName("o mesmo e-mail cadastra em duas aplicacoes e colide dentro de uma")
    void sameEmailFitsInTwoApplicationsAndCollidesInsideOne() {
        insertApplication(1001L, "portal-web");
        insertApplication(1002L, "app-campo");
        insertAccount(2001L, 1001L, null, "Pessoa Um", "Pessoa@Exemplo.Com");
        insertAccount(2002L, 1002L, null, "Pessoa Um", "Pessoa@Exemplo.Com");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertAccount(2003L, 1001L, null, "Outra", "pessoa@exemplo.com"))
                .withMessageContaining("UK_CLIENT_ACCOUNT_EMAIL");
    }

    // Medido neste banco: 150 caracteres com cedilha ocupam 299 bytes, e a semantica de
    // tamanho padrao e BYTE. Sem o CHAR na coluna esta linha seria recusada.
    @Test
    @DisplayName("nome com 150 caracteres acentuados entra inteiro")
    void accentedNameWithOneHundredAndFiftyCharactersFits() {
        insertApplication(1003L, "portal-acentuado");
        String name = "ç".repeat(150);

        insertAccount(2004L, 1003L, null, name, "acentuado@exemplo.com");

        Long storedLength = jdbcClient.sql("SELECT LENGTH(NAME) FROM " + SCHEMA_PREFIX
                        + "CLIENT_ACCOUNT WHERE ID = :id")
                .param("id", 2004L).query(Long.class).single();
        assertThat(storedLength).isEqualTo(150L);
    }

    // Com a empresa referida so por identificador, nada no banco impediria vincular conta
    // da aplicacao A a empresa da aplicacao B.
    @Test
    @DisplayName("conta nao pode apontar para empresa de outra aplicacao")
    void accountCannotReferenceCompanyFromAnotherApplication() {
        insertApplication(1004L, "aplicacao-a");
        insertApplication(1005L, "aplicacao-b");
        insertCompany(3001L, 1005L, "12345678000199");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertAccount(2005L, 1004L, 3001L, "Cruzada", "cruzada@exemplo.com"))
                .withMessageContaining("FK_CLIENT_ACCOUNT_COMPANY");
    }

    // O consumidor valida o codigo da aplicacao com a mesma expressao, e o cifrao do
    // Oracle casa antes de uma quebra de linha final, entao o guarda extra existe.
    @Test
    @DisplayName("codigo de aplicacao fora do formato e recusado pelo banco")
    void applicationCodeOutsideTheContractFormatIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertApplication(1006L, "Portal-Web"))
                .withMessageContaining("CK_APPLICATION_CODE_FORMAT");
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertApplication(1007L, "portal--web"))
                .withMessageContaining("CK_APPLICATION_CODE_FORMAT");
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertApplication(1008L, "portal-web\n"))
                .withMessageContaining("CK_APPLICATION_CODE_FORMAT");
    }

    /**
     * A restricao de formato nao pode mudar de veredito com a ordenacao da sessao.
     *
     * A versao anterior usava a expressao de intervalo [a-z0-9], e em Oracle o significado de
     * intervalo sai do NLS_SORT, que e parametro de sessao. Medido neste banco, antes da
     * correcao: com XSPANISH e com GERMAN o codigo com cedilha passava, com BINARY era
     * recusado. A sessao da aplicacao agora fixa BINARY, e por isso este caso **troca** a
     * ordenacao de proposito: ele prova que a restricao sozinha recusa, mesmo quando quem
     * grava entrou por fora da aplicacao e trouxe outra ordenacao.
     *
     * O valor e restaurado no fim porque a conexao volta para o pool, e deixar a ordenacao
     * trocada nela contaminaria os casos seguintes.
     */
    @Test
    @DisplayName("codigo com acento e recusado mesmo com outra ordenacao na sessao")
    void accentedCodeIsRejectedEvenUnderAnotherCollation() {
        jdbcClient.sql("ALTER SESSION SET NLS_SORT = XSPANISH").update();
        try {
            assertThatExceptionOfType(DataIntegrityViolationException.class)
                    .isThrownBy(() -> insertApplication(1010L, "portal-çweb"))
                    .withMessageContaining("CK_APPLICATION_CODE_FORMAT");
        } finally {
            jdbcClient.sql("ALTER SESSION SET NLS_SORT = BINARY").update();
        }
    }

    @Test
    @DisplayName("e-mail normalizado precisa ser a forma minuscula do e-mail cadastrado")
    void normalizedEmailMustBeTheLowercaseFormOfTheStoredEmail() {
        insertApplication(1009L, "portal-normalizacao");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbcClient.sql("INSERT INTO " + SCHEMA_PREFIX + "CLIENT_ACCOUNT"
                                + " (ID, APPLICATION_ID, NAME, EMAIL, EMAIL_NORMALIZED,"
                                + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                                + " VALUES (2006, 1009, 'Torta', 'Pessoa@Exemplo.Com',"
                                + " 'Pessoa@Exemplo.Com', '{argon2}forma', :now, :now)")
                        .param("now", OffsetDateTime.now()).update())
                .withMessageContaining("CK_CLIENT_ACCOUNT_EMAIL_LOWER");
    }
}
