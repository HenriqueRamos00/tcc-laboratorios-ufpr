package br.ufpr.tcc.backend_auth.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void swaggerPageAssetsAndConfigurationArePublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/swagger-ui/index.html")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        mvc.perform(get("/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/v3/api-docs/swagger-config")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.tryItOutEnabled").value(true));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Backend Auth API")));
    }

    @Test
    void specificationDescribesEachApiAndTheCorrectAuthenticationScope() throws Exception {
        JsonNode document = specification();
        assertThat(document.path("info").path("title").asText()).isEqualTo("Backend Auth API");
        JsonNode paths = document.path("paths");
        assertThat(paths.propertyNames()).containsExactlyInAnyOrder("/health", "/api/auth/login",
                "/api/admin/auth/login", "/api/setup/admin", "/api/users", "/api/admins", "/api/users/{id}", "/api/admins/{id}",
                "/api/services", "/api/services/{id}", "/api/services/{serviceId}/tokens", "/api/services/{serviceId}/tokens/{tokenId}");
        JsonNode schemes = document.path("components").path("securitySchemes");
        assertThat(schemes.path("serviceAdmin").path("type").asText()).isEqualTo("http");
        assertThat(schemes.path("serviceAdmin").path("scheme").asText()).isEqualTo("bearer");
        assertThat(schemes.path("serviceAdmin").has("bearerFormat")).isFalse();
        assertThat(schemes.path("setupToken").path("type").asText()).isEqualTo("apiKey");
        assertThat(schemes.path("setupToken").path("in").asText()).isEqualTo("header");
        assertThat(schemes.path("setupToken").path("name").asText()).isEqualTo("X-Setup-Token");
        assertThat(document.has("security")).isFalse();
        assertThat(paths.path("/health").path("get").has("security")).isFalse();
        assertThat(paths.path("/api/auth/login").path("post").at("/security/0/serviceToken").isArray()).isTrue();
        assertThat(schemes.path("serviceToken").path("scheme").asText()).isEqualTo("bearer");
        assertThat(paths.path("/api/users").path("get").path("security").toString()).contains("\"serviceToken\":[]");
        assertThat(paths.path("/api/users/{id}").path("get").path("security").toString()).contains("\"serviceToken\":[]");
        for (String path : new String[] {"/api/services", "/api/services/{serviceId}/tokens"}) {
            assertThat(paths.path(path).path("post").at("/security/0/serviceAdmin").isArray()).isTrue();
            assertThat(paths.path(path).path("get").path("security").toString()).contains("\"serviceAdmin\":[]");
        }
        assertThat(paths.path("/api/admin/auth/login").path("post").has("security")).isFalse();
        for (String path : new String[] {"/api/users", "/api/admins"}) {
            JsonNode operation = paths.path(path).path("post");
            assertThat(operation.at("/security/0/serviceAdmin").isArray()).isTrue();
            assertThat(operation.path("responses").propertyNames()).contains("201", "400", "401", "403", "409");
            assertThat(operation.path("responses").has("200")).isFalse();
            assertThat(paths.path(path).path("get").path("security").toString()).contains("\"serviceAdmin\":[]");
            for (String method : new String[] {"get", "put", "delete"}) {
                JsonNode detail = paths.path(path + "/{id}").path(method);
                assertThat(detail.path("security").toString()).contains("\"serviceAdmin\":[]");
                assertThat(detail.path("responses").has("404")).isTrue();
            }
            assertThat(paths.path(path + "/{id}").path("delete").path("responses").has("204")).isTrue();
        }
        JsonNode setup = paths.path("/api/setup/admin").path("post");
        assertThat(setup.at("/security/0/setupToken").isArray()).isTrue();
        assertThat(setup.path("responses").propertyNames()).contains("404", "410");
        assertThat(document.toString()).doesNotContain("senhaHash", "tokenHash", "administradorServico");
    }

    @Test
    void schemasIncludeUsableRequestsAndSafeResponseModels() throws Exception {
        JsonNode document = specification();
        JsonNode schemas = document.path("components").path("schemas");
        for (String name : new String[] {"CriarUsuarioRequest", "CriarAdministradorRequest", "LoginRequest"}) {
            JsonNode password = schemas.path(name).path("properties").path("password");
            assertThat(password.path("format").asText()).isEqualTo("password");
            assertThat(password.path("writeOnly").asBoolean()).isTrue();
            assertThat(password.path("example").asText()).isNotBlank();
        }
        assertThat(schemas.path("CriarUsuarioRequest").path("required").toString())
                .contains("name", "email", "password", "role", "serviceId");
        assertThat(schemas.path("CriarUsuarioRequest").at("/properties/password/minLength").asInt()).isEqualTo(12);
        assertThat(schemas.path("AdministradorResponse").path("properties").propertyNames())
                .containsExactlyInAnyOrder("id", "name", "email");
        assertThat(schemas.path("UsuarioResponse").path("properties").propertyNames())
                .containsExactlyInAnyOrder("id", "name", "email", "role", "serviceId");
        assertThat(schemas.path("LoginUsuarioResponse").path("properties").propertyNames())
                .containsExactlyInAnyOrder("id", "name", "email", "role", "serviceCode");
        assertThat(schemas.path("ApiErrorResponse").path("properties").has("message")).isTrue();
        assertThat(schemas.path("LoginAdministradorResponse").path("properties").propertyNames())
                .containsExactlyInAnyOrder("accessToken", "tokenType", "expiresIn", "user");
        for (String name : new String[] {"AtualizarUsuarioRequest", "AtualizarAdministradorRequest"}) {
            assertThat(schemas.path(name).path("required").toString()).contains("active").doesNotContain("password");
        }
        assertThat(document.at("/paths/~1api~1users/post/responses/201/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/UsuarioResponse");
        assertThat(document.at("/paths/~1api~1auth~1login/post/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/LoginUsuarioResponse");
    }

    @Test
    void publicDocumentationDoesNotAuthorizeAdministrativeWrites() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String path : new String[] {"/api/users", "/api/admins"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private JsonNode specification() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
}
