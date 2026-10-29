package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A rota de instalacao com o servico subido **sem** segredo configurado.
 *
 * O valor padrao da propriedade e vazio, e a comparacao em tempo constante trata segredo
 * ausente no pedido como cadeia vazia: com o ambiente tambem vazio, os dois resumos batem. A
 * unica coisa que ainda fecha a rota nesse estado e a condicao que recusa segredo em branco,
 * e nenhum caso da suite a exercitava, porque a classe que toca esta rota declara um segredo
 * de teste. Apagar aquela condicao deixava tudo verde e quem chegasse primeiro num banco
 * vazio viraria dono do servico inteiro.
 *
 * O compose recusa subir sem a variavel, e por isso o risco e de quem sobe de outra forma,
 * com java -jar, docker run ou orquestrador. A guarda e barata e a tomada de posse e
 * irreversivel, entao ela existe e agora tem caso.
 *
 * A classe tem contexto proprio porque a precondicao e a ausencia da propriedade, e ela
 * limpa a tabela de administradores pelo mesmo motivo que a classe de instalacao normal:
 * depender da ordem entre classes seria defeito silencioso.
 */
@AutoConfigureMockMvc
class SetupWithoutConfiguredSecretIntegrationTest extends OracleIntegrationTestBase {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private DatabaseProbe database;

    @BeforeEach
    void clearAdministrators() {
        database.statement("DELETE FROM ADMINISTRATIVE_SESSION").run();
        database.statement("DELETE FROM ADMINISTRATOR").run();
    }

    @Test
    @DisplayName("sem segredo configurado, a rota de instalacao nao existe para ninguem")
    void setupRouteDoesNotExistWithoutAConfiguredSecret() throws Exception {
        MvcResult withoutHeader = attemptSetup(null);
        MvcResult withAnyHeader = attemptSetup("qualquer-coisa");
        MvcResult withEmptyHeader = attemptSetup("");

        assertThat(withoutHeader.getResponse().getStatus()).isEqualTo(404);
        assertThat(withAnyHeader.getResponse().getStatus()).isEqualTo(404);
        assertThat(withEmptyHeader.getResponse().getStatus())
                .as("cabecalho vazio nao pode casar com ambiente vazio").isEqualTo(404);
        assertThat(administratorCount())
                .as("o banco continua sem administrador nenhum").isZero();
    }

    private MvcResult attemptSetup(String presentedSecret) throws Exception {
        var request = post("/api/setup/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Quem chegou primeiro","email":"primeiro@exemplo.com",
                         "password":"SenhaForte123"}""");
        if (presentedSecret != null) {
            request = request.header("X-Setup-Secret", presentedSecret);
        }
        return mockMvc.perform(request).andReturn();
    }

    private long administratorCount() {
        return database.statement("SELECT COUNT(*) FROM ADMINISTRATOR").count();
    }
}
