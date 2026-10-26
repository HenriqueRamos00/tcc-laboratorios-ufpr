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

    @Test
    @DisplayName("o usuario da aplicacao le e grava nas tabelas do esquema")
    void applicationUserReadsAndWritesTheSchema() {
        Long rows = jdbcClient.sql("SELECT COUNT(*) FROM " + SCHEMA_PREFIX + "ADMINISTRATOR")
                .query(Long.class).single();

        assertThat(rows).isNotNull();
    }
}
