package br.ufpr.tcc.backend_auth.infrastructure.config;

import br.ufpr.tcc.backend_auth.presentation.advice.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI backendAuthOpenApi() {
        Components components = new Components()
                .addSecuritySchemes("serviceAdmin", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer")
                        .description("Cole accessToken retornado por POST /api/admin/auth/login, sem o prefixo Bearer. "
                                + "Token opaco exclusivo para administrar este serviço; não é JWT."))
                .addSecuritySchemes("setupToken", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Setup-Token")
                        .description("Copie apenas o valor de token do link inicial exibido nos logs. "
                                + "Funciona somente antes de cadastrar o primeiro administrador."))
                .addResponses("InvalidRequest", error("Dados inválidos"))
                .addResponses("InvalidCredentials", error("Credenciais inválidas, conta inexistente ou inativa"))
                .addResponses("Forbidden", error("A conta não administra este serviço"))
                .addResponses("EmailConflict", error("E-mail já cadastrado"))
                .addResponses("AccountNotFound", error("Conta não encontrada neste escopo"))
                .addResponses("AccountConflict", error("E-mail já cadastrado ou tentativa de remover o último administrador ativo"))
                .addResponses("InvalidSetupLink", error("Link de configuração inválido"))
                .addResponses("SetupCompleted", error("O administrador inicial já foi cadastrado"));
        ModelConverters.getInstance().read(ApiErrorResponse.class).forEach(components::addSchemas);
        return new OpenAPI().components(components).info(new Info()
                .title("Backend Auth API").version("v1")
                .description("Login do portal sem token; administração com Bearer token opaco. "
                        + "Faça POST /api/admin/auth/login e cole accessToken em Authorize → serviceAdmin. "
                        + "O administrador deste serviço não recebe um perfil no Portal Web."));
    }

    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(
                "application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
    }
}
