package br.ufpr.tcc.backend_auth.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CompanyApiTests extends ServiceTestSupport {
    private static final String PASSWORD = "Valid123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    String adminToken;

    @BeforeEach
    void prepare() throws Exception {
        prepararServico();
        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail("admin@example.test");
        admin.setSenhaHash(encoder.encode("Administrator 123!"));
        admin.setAdministradorServico(true);
        usuarios.saveAndFlush(admin);
        String login = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", admin.getEmail(), "password", "Administrator 123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        adminToken = json.readTree(login).path("accessToken").asText();
    }

    @Test
    void companyCrudNormalizesNamesAndNumericOrAlphanumericCnpj() throws Exception {
        String numeric = company("  Empresa numérica  ", "04.252.011/0001-10");
        mvc.perform(auth(get("/api/companies/" + numeric))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString()).andExpect(jsonPath("$.name").value("Empresa numérica"))
                .andExpect(jsonPath("$.cnpj").value("04252011000110"));
        String alpha = company("Alfanumérica", "12.abc.345/01de-35");
        mvc.perform(auth(get("/api/companies"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].id").value(alpha))
                .andExpect(jsonPath("$[0].cnpj").value("12ABC34501DE35"));
        mvc.perform(auth(put("/api/companies/" + numeric)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Atualizada", "cnpj", "11.222.333/0001-81"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cnpj").value("11222333000181"));
        mvc.perform(auth(delete("/api/companies/" + numeric))).andExpect(status().isNoContent());
        for (String path : List.of("/api/companies/" + numeric, "/api/companies/999999")) {
            mvc.perform(auth(get(path))).andExpect(status().isNotFound());
            mvc.perform(auth(delete(path))).andExpect(status().isNotFound());
            mvc.perform(auth(put(path)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Empresa\",\"cnpj\":\"04252011000110\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(auth(get("/api/companies/invalid"))).andExpect(status().isBadRequest());
    }

    @Test
    void companyManagementIsAdminOnlyForAllMethods() throws Exception {
        for (String path : List.of("/api/companies", "/api/companies/1")) {
            for (var builder : List.of(get(path), post(path), put(path), delete(path))) {
                mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isUnauthorized());
            }
            for (var builder : List.of(get(path), post(path), put(path), delete(path))) {
                mvc.perform(builder.header("Authorization", "Bearer " + serviceToken).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "00000000000000", "11111111111111", "04252011000111", "0425201100011", "042520110001100",
            "12ABC34501DE36", "12ABC34501DEAB", "04_252011000110", "04.252.011/0001-10junk", "12ábc34501de35"})
    void invalidCnpjsAreRejected(String cnpj) throws Exception {
        mvc.perform(auth(post("/api/companies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Empresa", "cnpj", cnpj))))
                .andExpect(status().isBadRequest());
        assertThat(empresas.count()).isZero();
    }

    @Test
    void namesAreRequiredAndCnpjIsUniqueAlsoAfterNormalizationAndOnUpdate() throws Exception {
        for (String name : List.of(" ", "x".repeat(151))) {
            mvc.perform(auth(post("/api/companies")).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", name, "cnpj", "04252011000110"))))
                    .andExpect(status().isBadRequest());
        }
        company("Empresa", "04252011000110");
        mvc.perform(auth(post("/api/companies")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duplicada\",\"cnpj\":\" 04.252.011/0001-10 \"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("CNPJ já cadastrado"));
        String other = company("Outra", "11222333000181");
        mvc.perform(auth(put("/api/companies/" + other)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duplicada\",\"cnpj\":\"04252011000110\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentDuplicateCnpjReturnsOneCreatedAndOneConflict() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return createDuplicate(); });
            var second = executor.submit(() -> { start.await(); return createDuplicate(); });
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(empresas.count()).isEqualTo(1);
    }

    @Test
    void multipleCompanyLinksAppearInLoginAndScopedUserResponsesWithoutGrantingOtherCompanies() throws Exception {
        String numeric = company("Empresa", "04252011000110");
        String alpha = company("Alfanumérica", "12ABC34501DE35");
        company("Não autorizada", "11222333000181");
        String id = client(List.of(alpha, numeric, alpha));
        mvc.perform(get("/api/users/" + id).header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyIds.length()").value(2))
                .andExpect(jsonPath("$.authorizations.companyCnpjs[0]").value("04252011000110"))
                .andExpect(jsonPath("$.authorizations.companyCnpjs[1]").value("12ABC34501DE35"))
                .andExpect(jsonPath("$.authorizations.companyCnpjs.length()").value(2));
        String body = login().andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.serviceCode").value("portal-web")).andExpect(jsonPath("$.serviceId").doesNotExist())
                .andExpect(jsonPath("$.authorizations.companyCnpjs.length()").value(2))
                .andExpect(jsonPath("$.companyIds").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("11222333000181", "senhaHash", "falhasLogin", "loginBloqueadoAte", "accessToken");
        // The same company registry does not open access to clients of another service.
        Servico other = new Servico();
        other.setNome("Outro serviço");
        other.setCodigo("other");
        other = servicos.saveAndFlush(other);
        mvc.perform(auth(put("/api/users/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Cliente", "email", "client@example.test", "role", "CLIENTE",
                                "active", true, "serviceId", other.getId().toString(), "companyIds", List.of(numeric, alpha)))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/" + id).header("Authorization", "Bearer " + serviceToken)).andExpect(status().isNotFound());
        login().andExpect(status().isUnauthorized());
    }

    @Test
    void updatesReplaceAssociationsAndCnpjEditsTakeEffectOnNextLogin() throws Exception {
        String first = company("Empresa", "04252011000110");
        String second = company("Alfanumérica", "12ABC34501DE35");
        String id = client(List.of(first, second));
        mvc.perform(auth(put("/api/companies/" + first)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Atualizada\",\"cnpj\":\"11222333000181\"}"))
                .andExpect(status().isOk());
        login().andExpect(status().isOk()).andExpect(jsonPath("$.authorizations.companyCnpjs[0]").value("11222333000181"));
        mvc.perform(auth(delete("/api/companies/" + first))).andExpect(status().isConflict());
        updateClient(id, List.of(second));
        mvc.perform(auth(delete("/api/companies/" + first))).andExpect(status().isNoContent());
        login().andExpect(status().isOk()).andExpect(jsonPath("$.authorizations.companyCnpjs.length()").value(1))
                .andExpect(jsonPath("$.authorizations.companyCnpjs[0]").value("12ABC34501DE35"));
        updateClient(id, List.of());
        login().andExpect(status().isOk()).andExpect(jsonPath("$.authorizations.companyCnpjs").isEmpty());
        mvc.perform(auth(delete("/api/companies/" + second))).andExpect(status().isNoContent());
    }

    @Test
    void missingCompaniesRollbackAssignmentAndOmittedCompaniesMeanNoAssociation() throws Exception {
        String company = company("Empresa", "04252011000110");
        String id = client(List.of(company));
        mvc.perform(auth(put("/api/users/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(clientRequest(List.of("999999")))))
                .andExpect(status().isNotFound());
        login().andExpect(status().isOk()).andExpect(jsonPath("$.authorizations.companyCnpjs[0]").value("04252011000110"));
        var request = new java.util.LinkedHashMap<>(clientRequest(List.of()));
        request.remove("companyIds");
        mvc.perform(auth(put("/api/users/" + id)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyIds").isEmpty());
        login().andExpect(status().isOk()).andExpect(jsonPath("$.authorizations.companyCnpjs").isEmpty());
        request.put("email", "new@example.test");
        request.put("companyIds", List.of("999999"));
        mvc.perform(auth(post("/api/users")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        assertThat(usuarios.findByEmail("new@example.test")).isEmpty();
    }

    @Test
    void deletingAClientRemovesItsLinksAndPreservesCompanies() throws Exception {
        String company = company("Empresa", "04252011000110");
        String id = client(List.of(company));
        mvc.perform(auth(delete("/api/users/" + id))).andExpect(status().isNoContent());
        assertThat(empresas.count()).isEqualTo(1);
        mvc.perform(auth(delete("/api/companies/" + company))).andExpect(status().isNoContent());
    }

    private int createDuplicate() throws Exception {
        return mvc.perform(auth(post("/api/companies")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Concorrente\",\"cnpj\":\"04252011000110\"}"))
                .andReturn().getResponse().getStatus();
    }

    private String company(String name, String cnpj) throws Exception {
        String body = mvc.perform(auth(post("/api/companies")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "cnpj", cnpj))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(body);
        assertThat(response.propertyNames()).containsExactlyInAnyOrder("id", "name", "cnpj");
        return response.path("id").asText();
    }

    private String client(List<String> companies) throws Exception {
        String body = mvc.perform(auth(post("/api/users")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(clientRequest(companies))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asText();
    }

    private void updateClient(String id, List<String> companies) throws Exception {
        mvc.perform(auth(put("/api/users/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(clientRequest(companies))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> clientRequest(List<String> companies) {
        return Map.of("name", "Cliente", "email", "client@example.test", "password", PASSWORD, "role", "CLIENTE", "active", true,
                "serviceId", servico.getId().toString(), "companyIds", companies);
    }

    private org.springframework.test.web.servlet.ResultActions login() throws Exception {
        return mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", "client@example.test", "password", PASSWORD))));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + adminToken);
    }
}
