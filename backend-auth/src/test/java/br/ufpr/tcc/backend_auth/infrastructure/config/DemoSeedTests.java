package br.ufpr.tcc.backend_auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.ufpr.tcc.backend_auth.application.service.TokenOpaco;
import br.ufpr.tcc.backend_auth.application.service.TokenServicoService;
import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_demo_seed;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "demo.seed.enabled=true",
        "demo.seed.service-token=" + DemoSeedTests.TOKEN
})
@AutoConfigureMockMvc
class DemoSeedTests {
    static final String TOKEN = "svc_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    @Autowired DemoSeedRunner seed;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired ServicoRepository servicos;
    @Autowired TokenServicoRepository tokens;
    @Autowired SessaoAdministrativaRepository sessoes;
    @Autowired TokenServicoService tokenService;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void prepare() {
        clean();
        seed.run(null);
    }

    @Test
    void freshSeedAuthenticatesTheThreeAccountsWithSeparateScopes() throws Exception {
        assertCounts();
        for (int client = 1; client <= 2; client++) {
            String email = "client" + client + "@lactec.com";
            assertThat(encoder.matches(email, usuarios.findByEmail(email).orElseThrow().getSenhaHash())).isTrue();
            mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + TOKEN)
                            .contentType(MediaType.APPLICATION_JSON).content(login(email, email)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CLIENTE"))
                    .andExpect(jsonPath("$.serviceCode").value("portal-web"))
                    .andExpect(jsonPath("$.authorizations.companyCnpjs.length()").value(1))
                    .andExpect(jsonPath("$.authorizations.companyCnpjs[0]")
                            .value(client == 1 ? "04252011000110" : "11222333000181"));
            mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, email)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("lactec@lactec.com", "lactec@lactec.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(login("lactec@lactec.com", "lactec@lactec.com")))
                .andExpect(status().isUnauthorized());
        assertThat(tokenService.autenticar(TOKEN)).isEqualTo(servicos.findByCodigo("portal-web").orElseThrow().getId());
    }

    @Test
    void rerunPreservesPasswordsAccountStateAndCompanyPermissions() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Usuario client = usuarios.findByEmail("client1@lactec.com").orElseThrow();
            client.setSenhaHash(encoder.encode("ChangedPassword123"));
            client.setNome("Nome editado");
            client.setAtivo(false);
            client.getEmpresas().clear();
        });
        String hash = usuarios.findByEmail("client1@lactec.com").orElseThrow().getSenhaHash();
        var previousToken = tokens.findByTokenHash(TokenOpaco.hash(TOKEN)).orElseThrow();
        seed.run(null);
        assertCounts();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Usuario client = usuarios.findByEmail("client1@lactec.com").orElseThrow();
            assertThat(client.getSenhaHash()).isEqualTo(hash);
            assertThat(client.getNome()).isEqualTo("Nome editado");
            assertThat(client.isAtivo()).isFalse();
            assertThat(client.getEmpresas()).isEmpty();
        });
        var currentToken = tokens.findByTokenHash(TokenOpaco.hash(TOKEN)).orElseThrow();
        assertThat(currentToken.getId()).isEqualTo(previousToken.getId());
        assertThat(currentToken.getExpiraEm()).isEqualTo(previousToken.getExpiraEm());
    }

    @Test
    void expiredDemoTokenIsRenewedWithoutAnotherRecord() {
        var token = tokens.findByTokenHash(TokenOpaco.hash(TOKEN)).orElseThrow();
        token.setExpiraEm(Instant.now().minusSeconds(60));
        tokens.saveAndFlush(token);
        seed.run(null);
        var renewed = tokens.findByTokenHash(TokenOpaco.hash(TOKEN)).orElseThrow();
        assertThat(renewed.getId()).isEqualTo(token.getId());
        assertThat(renewed.getExpiraEm()).isAfter(Instant.now());
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(tokenService.autenticar(TOKEN)).isEqualTo(token.getServico().getId());
    }

    @Test
    void conflictingReservedEmailRollsBackTheWholeSeed() {
        clean();
        Usuario collision = new Usuario();
        collision.setNome("Administrador existente");
        collision.setEmail("client2@lactec.com");
        collision.setSenhaHash(encoder.encode("ExistingPassword123"));
        collision.setAdministradorServico(true);
        usuarios.saveAndFlush(collision);
        assertThatThrownBy(() -> seed.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client2@lactec.com");
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(empresas.count()).isZero();
        assertThat(servicos.count()).isZero();
        assertThat(tokens.count()).isZero();
        assertThat(encoder.matches("ExistingPassword123", usuarios.findByEmail("client2@lactec.com")
                .orElseThrow().getSenhaHash())).isTrue();
    }

    @Test
    void demoTokenAlreadyOwnedByAnotherServiceIsRejected() {
        Servico other = new Servico();
        other.setNome("Outro serviço");
        other.setCodigo("other-service");
        other = servicos.saveAndFlush(other);
        var token = tokens.findByTokenHash(TokenOpaco.hash(TOKEN)).orElseThrow();
        token.setServico(other);
        tokens.saveAndFlush(token);
        assertThatThrownBy(() -> seed.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outro serviço");
        assertThat(tokens.findByTokenHash(TokenOpaco.hash(TOKEN)).orElseThrow().getServico().getId()).isEqualTo(other.getId());
    }

    @Test
    void publicRegistrationStillRejectsEmailAsClientPassword() throws Exception {
        String response = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("lactec@lactec.com", "lactec@lactec.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adminToken = json.readTree(response).path("accessToken").asText();
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "name", "Novo cliente", "email", "other@lactec.com", "password", "other@lactec.com",
                                "role", "CLIENTE", "serviceId", servicos.findByCodigo("portal-web").orElseThrow().getId()))))
                .andExpect(status().isBadRequest());
    }

    private String login(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private void clean() {
        sessoes.deleteAll();
        tokens.deleteAll();
        usuarios.deleteAll();
        empresas.deleteAll();
        servicos.deleteAll();
    }

    private void assertCounts() {
        assertThat(usuarios.count()).isEqualTo(3);
        assertThat(empresas.count()).isEqualTo(2);
        assertThat(servicos.count()).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
    }
}
