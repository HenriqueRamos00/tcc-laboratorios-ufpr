package br.ufpr.tcc.auth.shared.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Prova que a migracao rodou e que o mapeamento das entidades confere com o esquema que
 * ela produziu. Subir o contexto ja e metade da afirmacao, porque ddl-auto esta em
 * validate: tabela ausente ou tipo divergente derruba a subida.
 */
class SchemaMigrationIntegrationTest extends OracleIntegrationTestBase {

    @Autowired
    private DatabaseProbe database;

    @Test
    @DisplayName("a migracao cria as quatro tabelas e o mapeamento valida contra elas")
    void migrationCreatesEverySchemaObject() {
        for (String table : new String[] {"APPLICATION", "COMPANY", "CLIENT_ACCOUNT", "ADMINISTRATOR"}) {
            Long rows = database.statement("SELECT COUNT(*) FROM " + SCHEMA_PREFIX + table)
                    .count();

            // A afirmacao e que a tabela existe e responde, nao que esteja vazia: o
            // contentor e reaproveitado pela suite inteira e outras classes gravam nela.
            assertThat(rows).as("tabela %s", table).isNotNull();
        }
    }

    @Test
    @DisplayName("a aplicacao enxerga o esquema pelo usuario restrito, sem ser dona dele")
    void applicationReadsTheSchemaItDoesNotOwn() {
        String connectedUser = database.statement("SELECT USER FROM DUAL").value(String.class);

        assertThat(connectedUser).isEqualToIgnoringCase(APPLICATION_USERNAME);
    }
}
