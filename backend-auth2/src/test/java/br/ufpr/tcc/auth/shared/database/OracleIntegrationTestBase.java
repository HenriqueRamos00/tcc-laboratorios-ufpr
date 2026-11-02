package br.ufpr.tcc.auth.shared.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base das suites que conversam com um Oracle de verdade.
 *
 * O contentor e um campo estatico iniciado uma vez e reaproveitado pela suite inteira. Nao
 * se usa withReuse: a documentacao do Testcontainers declara aquele recurso experimental e
 * inadequado para integracao continua, e banco que sobrevive entre execucoes faria a soma
 * de verificacao do Flyway brigar com a migracao editada.
 *
 * A etiqueta de tipo em ServiceConnection nao e enfeite. O modulo spring-boot-flyway
 * registra uma fabrica de detalhes de conexao que casa com qualquer contentor JDBC
 * anotado e devolve a credencial do proprio contentor, e o bean que le spring.flyway.user
 * so existe na ausencia dela. Sem a etiqueta a migracao rodaria como o usuario da
 * aplicacao, e a separacao de dois usuarios deixaria de existir sem derrubar teste nenhum.
 */
@SpringBootTest
@Tag("integration")
@Import(DatabaseProbe.class)
public abstract class OracleIntegrationTestBase {

    static final String SCHEMA_OWNER_USERNAME = "auth_owner";
    static final String APPLICATION_USERNAME = "auth_app";
    static final String SCHEMA_PREFIX = "AUTH_OWNER.";

    private static final String SYSTEM_USERNAME = "system";
    private static final String READ_WRITE_ROLE = "AUTH_READWRITE";
    private static final String CONTAINER_PASSWORD = "senha-do-contentor-descartavel";

    @ServiceConnection(type = JdbcConnectionDetails.class)
    static final OracleContainer ORACLE =
            new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:slim-faststart"))
                    .withUsername(APPLICATION_USERNAME)
                    .withPassword(CONTAINER_PASSWORD);

    static {
        ORACLE.start();
        provisionSchemaUsers();
    }

    /**
     * O Flyway roda com credencial propria, do dono do esquema. A URL fica de fora: sem ela
     * o Boot deriva o acesso de migracao do acesso da aplicacao e troca apenas usuario e
     * senha, o que impede que as duas pontas apontem para bancos diferentes por engano.
     */
    @DynamicPropertySource
    static void migrationRunsAsSchemaOwner(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.user", () -> SCHEMA_OWNER_USERNAME);
        registry.add("spring.flyway.password", () -> CONTAINER_PASSWORD);
    }

    /**
     * Consulta pela conexao do dono do esquema.
     *
     * Ela existe porque a lista de concessoes precisa ser conferida pelo lado de quem
     * concede: o usuario restrito nao enxerga tabela sobre a qual nao tem privilegio, e
     * seria justo a tabela esquecida que ficaria invisivel na consulta dele.
     *
     * Este e o unico acesso ao banco do modulo que nao passa pelo JPA, e nao pode passar:
     * ele abre conexao com **outra credencial**, a do dono do esquema, e a unidade de
     * persistencia da aplicacao tem a credencial restrita. Mesmo motivo vale para a
     * preparacao dos usuarios logo abaixo, que roda antes de existir contexto de aplicacao.
     */
    static List<String> schemaOwnerQuery(String sql) {
        List<String> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                        ORACLE.getJdbcUrl(), SCHEMA_OWNER_USERNAME, CONTAINER_PASSWORD);
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            while (results.next()) {
                rows.add(results.getString(1));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Nao foi possivel consultar como dono do esquema",
                    failure);
        }
        return rows;
    }

    /**
     * Refaz o usuario da aplicacao com os mesmos privilegios que o script de inicializacao
     * do compose concede, porque a imagem cria o usuario dela com direito de criar tabela.
     * Sem isto a suite afirmaria sobre um usuario mais poderoso que o de producao.
     */
    private static void provisionSchemaUsers() {
        List<String> statements = List.of(
                "DROP USER " + APPLICATION_USERNAME + " CASCADE",
                "CREATE USER " + SCHEMA_OWNER_USERNAME + " IDENTIFIED BY \"" + CONTAINER_PASSWORD
                        + "\" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS",
                "GRANT CREATE SESSION, CREATE TABLE TO " + SCHEMA_OWNER_USERNAME,
                "CREATE USER " + APPLICATION_USERNAME + " IDENTIFIED BY \"" + CONTAINER_PASSWORD
                        + "\" DEFAULT TABLESPACE USERS QUOTA 0 ON USERS",
                "GRANT CREATE SESSION TO " + APPLICATION_USERNAME,
                "CREATE ROLE " + READ_WRITE_ROLE,
                "GRANT " + READ_WRITE_ROLE + " TO " + APPLICATION_USERNAME);
        try (Connection connection = DriverManager.getConnection(
                        ORACLE.getJdbcUrl(), SYSTEM_USERNAME, CONTAINER_PASSWORD);
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(
                    "Nao foi possivel preparar os usuarios do esquema no contentor", failure);
        }
    }
}
