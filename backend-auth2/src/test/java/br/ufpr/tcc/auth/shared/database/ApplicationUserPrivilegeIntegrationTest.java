package br.ufpr.tcc.auth.shared.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prova que o usuario que a aplicacao usa em tempo de execucao nao pode alterar o esquema.
 *
 * Sem este caso, a separacao de dois usuarios e afirmacao em documento: o teste nao
 * distinguiria "a aplicacao foi proibida de criar tabela" de "a aplicacao nunca tentou".
 * A defesa e dupla de proposito, privilegio ausente e cota zero no espaco de tabela, entao
 * devolver so um dos dois ainda deixa a gravacao falhar.
 *
 * Esta classe nao e transacional: comando de definicao faz confirmacao implicita no
 * Oracle, e uma transacao em volta nao desfaria nada.
 */
class ApplicationUserPrivilegeIntegrationTest extends OracleIntegrationTestBase {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("o usuario da aplicacao nao cria tabela")
    void applicationUserCannotCreateTable() {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcClient.sql("CREATE TABLE INTRUSO (ID NUMBER(19))").update())
                .withStackTraceContaining("ORA-01031");
    }

    @Test
    @DisplayName("o usuario da aplicacao nao derruba tabela do dono do esquema")
    void applicationUserCannotDropTheOwnerTable() {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> jdbcClient.sql("DROP TABLE " + SCHEMA_PREFIX + "APPLICATION").update())
                .withStackTraceContaining("ORA-01031");
    }

    /**
     * Os dois casos acima provam que **um comando falha**, e nao que a lista de privilegios e
     * a declarada. Acrescentar um papel ao usuario da aplicacao, por exemplo RESOURCE, nao
     * traz CREATE TABLE nesta versao do Oracle, entao ORA-01031 continuaria valendo e os dois
     * continuariam verdes com o usuario carregando poder que ele nao deveria ter.
     *
     * Este caso cobra a lista inteira, e e a forma de a separacao de privilegio deixar de ser
     * afirmacao em documento.
     */
    @Test
    @DisplayName("o usuario da aplicacao tem apenas entrada de sessao e o papel de leitura e escrita")
    void applicationUserHasNothingBeyondTheDeclaredPrivileges() {
        java.util.List<String> systemPrivileges = jdbcClient
                .sql("SELECT PRIVILEGE FROM USER_SYS_PRIVS ORDER BY PRIVILEGE")
                .query(String.class).list();
        java.util.List<String> roles = jdbcClient
                .sql("SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS ORDER BY GRANTED_ROLE")
                .query(String.class).list();
        java.util.List<String> directTablePrivileges = jdbcClient
                .sql("SELECT PRIVILEGE FROM USER_TAB_PRIVS WHERE GRANTEE = USER")
                .query(String.class).list();

        assertThat(systemPrivileges).containsExactly("CREATE SESSION");
        assertThat(roles).containsExactly("AUTH_READWRITE");
        assertThat(directTablePrivileges)
                .as("tudo passa pelo papel, para a revisao ter um lugar so").isEmpty();
    }

    /**
     * Migracao que cria tabela e esquece a concessao nao quebra a migracao: ela quebra na
     * primeira consulta, com ORA-00942, e so se algum outro caso tocar aquela tabela. Como as
     * concessoes vivem espalhadas por um arquivo de migracao por fatia, esquecer uma e o
     * defeito mais facil de cometer.
     */
    @Test
    @DisplayName("toda tabela do dono concede as quatro operacoes ao papel da aplicacao")
    void everyOwnerTableGrantsTheFourOperationsToTheRole() {
        java.util.List<String> withoutTheFullGrant = schemaOwnerQuery("""
                SELECT owned.TABLE_NAME
                  FROM USER_TABLES owned
                 WHERE owned.TABLE_NAME <> 'flyway_schema_history'
                   AND (SELECT COUNT(DISTINCT granted.PRIVILEGE)
                          FROM USER_TAB_PRIVS granted
                         WHERE granted.TABLE_NAME = owned.TABLE_NAME
                           AND granted.GRANTEE = 'AUTH_READWRITE'
                           AND granted.PRIVILEGE IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE')) < 4
                 ORDER BY owned.TABLE_NAME
                """);

        assertThat(withoutTheFullGrant)
                .as("tabela sem as quatro concessoes responde ORA-00942 em producao").isEmpty();
    }

    @Test
    @DisplayName("o usuario da aplicacao le e grava nas tabelas do esquema")
    void applicationUserReadsAndWritesTheSchema() {
        Long rows = jdbcClient.sql("SELECT COUNT(*) FROM " + SCHEMA_PREFIX + "ADMINISTRATOR")
                .query(Long.class).single();

        assertThat(rows).isNotNull();
    }
}
