package br.ufpr.tcc.auth.shared.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.OffsetDateTime;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
    private DatabaseProbe database;

    private void insertApplication(long id, String code) {
        database.statement("INSERT INTO " + SCHEMA_PREFIX + "APPLICATION"
                        + " (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :name, TRUE, :now, :now)")
                .with("id", id).with("code", code).with("name", "Aplicacao " + code)
                .with("now", OffsetDateTime.now()).run();
    }

    private void insertCompany(long id, String cnpj) {
        database.statement("INSERT INTO " + SCHEMA_PREFIX + "COMPANY"
                        + " (ID, NAME, CNPJ, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :name, :cnpj, :now, :now)")
                .with("id", id)
                .with("name", "Empresa " + id).with("cnpj", cnpj)
                .with("now", OffsetDateTime.now()).run();
    }

    private void insertAccount(long id, long applicationId, String name, String email) {
        database.statement("INSERT INTO " + SCHEMA_PREFIX + "CLIENT_ACCOUNT"
                        + " (ID, APPLICATION_ID, NAME, EMAIL, EMAIL_NORMALIZED,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :applicationId, :name, :email, :normalized,"
                        + " '{argon2}forma-guardada-de-teste', :now, :now)")
                .with("id", id).with("applicationId", applicationId)
                .with("name", name).with("email", email)
                .with("normalized", email.toLowerCase(Locale.ROOT))
                .with("now", OffsetDateTime.now()).run();
    }

    // Unicidade global faria o cadastro da aplicacao B recusar um e-mail por causa da
    // aplicacao A, o que entrega a existencia da conta de uma para a outra.
    @Test
    @DisplayName("o mesmo e-mail cadastra em duas aplicacoes e colide dentro de uma")
    void sameEmailFitsInTwoApplicationsAndCollidesInsideOne() {
        insertApplication(1001L, "portal-web");
        insertApplication(1002L, "app-campo");
        insertAccount(2001L, 1001L, "Pessoa Um", "Pessoa@Exemplo.Com");
        insertAccount(2002L, 1002L, "Pessoa Um", "Pessoa@Exemplo.Com");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertAccount(2003L, 1001L, "Outra", "pessoa@exemplo.com"))
                .withMessageContaining("UK_CLIENT_ACCOUNT_EMAIL");
    }

    // Medido neste banco: 150 caracteres com cedilha ocupam 299 bytes, e a semantica de
    // tamanho padrao e BYTE. Sem o CHAR na coluna esta linha seria recusada.
    @Test
    @DisplayName("nome com 150 caracteres acentuados entra inteiro")
    void accentedNameWithOneHundredAndFiftyCharactersFits() {
        insertApplication(1003L, "portal-acentuado");
        String name = "ç".repeat(150);

        insertAccount(2004L, 1003L, name, "acentuado@exemplo.com");

        Long storedLength = database.statement("SELECT LENGTH(NAME) FROM " + SCHEMA_PREFIX
                        + "CLIENT_ACCOUNT WHERE ID = :id")
                .with("id", 2004L).count();
        assertThat(storedLength).isEqualTo(150L);
    }

    // Com a empresa referida so por identificador, nada no banco impediria vincular conta
    // da aplicacao A a empresa da aplicacao B.
    private void link(long accountId, long companyId) {
        database.statement("INSERT INTO " + SCHEMA_PREFIX + "CLIENT_ACCOUNT_COMPANY"
                        + " (CLIENT_ACCOUNT_ID, COMPANY_ID) VALUES (:accountId, :companyId)")
                .with("accountId", accountId).with("companyId", companyId).run();
    }

    private long linksOf(long accountId) {
        return database.statement("SELECT COUNT(*) FROM " + SCHEMA_PREFIX
                        + "CLIENT_ACCOUNT_COMPANY WHERE CLIENT_ACCOUNT_ID = :accountId")
                .with("accountId", accountId).count();
    }

    @Test
    @DisplayName("o mesmo CNPJ nao entra duas vezes, porque empresa nao pertence a aplicacao")
    void sameCnpjIsUniqueAcrossTheService() {
        insertCompany(3001L, "12345678000199");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertCompany(3002L, "12345678000199"))
                .withMessageContaining("UK_COMPANY_CNPJ");
    }

    @Test
    @DisplayName("contas de aplicacoes diferentes representam a mesma empresa, uma vez cada")
    void accountsOfDifferentApplicationsLinkToTheSameCompanyOnce() {
        insertApplication(1004L, "aplicacao-a");
        insertApplication(1005L, "aplicacao-b");
        insertCompany(3003L, "22345678000199");
        insertAccount(2005L, 1004L, "Pessoa A", "a@exemplo.com");
        insertAccount(2007L, 1005L, "Pessoa B", "b@exemplo.com");
        link(2005L, 3003L);
        link(2007L, 3003L);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> link(2005L, 3003L))
                .withMessageContaining("PK_CLIENT_ACCOUNT_COMPANY");
    }

    @Test
    @DisplayName("vinculo nao aponta para empresa que nao existe")
    void linkCannotReferenceACompanyThatDoesNotExist() {
        insertApplication(1011L, "aplicacao-vinculo");
        insertAccount(2008L, 1011L, "Pessoa", "vinculo@exemplo.com");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> link(2008L, 3999L))
                .withMessageContaining("FK_ACCOUNT_COMPANY_COMPANY");
    }

    @Test
    @DisplayName("empresa vinculada nao e apagada, e apagar a conta leva os vinculos dela")
    void linkedCompanyIsKeptAndRemovingTheAccountRemovesItsLinks() {
        insertApplication(1012L, "aplicacao-exclusao");
        insertCompany(3004L, "32345678000199");
        insertAccount(2009L, 1012L, "Pessoa", "exclusao@exemplo.com");
        link(2009L, 3004L);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> database.statement("DELETE FROM " + SCHEMA_PREFIX
                        + "COMPANY WHERE ID = 3004").run())
                .withMessageContaining("FK_ACCOUNT_COMPANY_COMPANY");

        database.statement("DELETE FROM " + SCHEMA_PREFIX + "CLIENT_ACCOUNT WHERE ID = 2009").run();

        assertThat(linksOf(2009L)).isZero();
    }

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
        database.statement("ALTER SESSION SET NLS_SORT = XSPANISH").run();
        try {
            assertThatExceptionOfType(DataIntegrityViolationException.class)
                    .isThrownBy(() -> insertApplication(1010L, "portal-çweb"))
                    .withMessageContaining("CK_APPLICATION_CODE_FORMAT");
        } finally {
            database.statement("ALTER SESSION SET NLS_SORT = BINARY").run();
        }
    }

    @Test
    @DisplayName("e-mail normalizado precisa ser a forma minuscula do e-mail cadastrado")
    void normalizedEmailMustBeTheLowercaseFormOfTheStoredEmail() {
        insertApplication(1009L, "portal-normalizacao");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> database.statement("INSERT INTO " + SCHEMA_PREFIX + "CLIENT_ACCOUNT"
                                + " (ID, APPLICATION_ID, NAME, EMAIL, EMAIL_NORMALIZED,"
                                + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                                + " VALUES (2006, 1009, 'Torta', 'Pessoa@Exemplo.Com',"
                                + " 'Pessoa@Exemplo.Com', '{argon2}forma', :now, :now)")
                        .with("now", OffsetDateTime.now()).run())
                .withMessageContaining("CK_CLIENT_ACCOUNT_EMAIL_LOWER");
    }
}
