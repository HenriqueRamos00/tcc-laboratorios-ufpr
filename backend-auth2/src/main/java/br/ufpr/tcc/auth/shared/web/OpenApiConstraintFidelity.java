package br.ufpr.tcc.auth.shared.web;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Corrige duas coisas que o gerador do contrato publica diferente do que o servidor cobra.
 *
 * A primeira e a ancora da expressao. O validador do Jakarta casa a cadeia inteira, e por isso
 * a regra da casa proibe escrever ancora na anotacao de validacao. O gerador copia a expressao
 * sem traduzir, e `pattern` em JSON Schema e casamento **parcial** por definicao: publicado
 * assim, `[0-9]{14}` autoriza `abc01234567890123x` e `CLIENTE` autoriza `ADMIN-CLIENTE-X`.
 * Cliente gerado a partir do artefato passaria a aceitar o que o servidor recusa com 400, e o
 * codigo da aplicacao e justamente o campo que o consumidor valida na inicializacao dele.
 *
 * A segunda e o campo de senha. O gerador deriva o minimo do `@Size`, que nao declara minimo
 * de proposito, porque a politica de oito caracteres mais maiuscula, minuscula e digito mora
 * no caso de uso e nao em anotacao. Publicado como `minLength: 0`, o artefato afirma que
 * senha vazia serve. Aqui o minimo e declarado onde ele e verdade, nas rotas de cadastro e de
 * troca, e nunca no login, onde nao existe minimo por decisao de contrato.
 *
 * Isto roda no gerador e nao no fonte porque foi medido: anotar `@Schema(pattern = ...)` ao
 * lado do `@Pattern` nao prevalece, e `@Schema(minLength = 8)` ao lado do `@Size` tambem nao.
 */
@Configuration
class OpenApiConstraintFidelity {

    private static final int MINIMUM_PASSWORD_LENGTH = 8;

    private static final String LOGIN_REQUEST_SUFFIX = "LoginRequest";

    @Bean
    OpenApiCustomizer constraintFidelity() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach(OpenApiConstraintFidelity::correct);
        };
    }

    private static void correct(String schemaName, Schema<?> schema) {
        if (schema.getProperties() == null) {
            return;
        }
        schema.getProperties().forEach((propertyName, property) -> {
            anchorPattern(property);
            if ("password".equals(propertyName)) {
                markAsSecret(schemaName, property);
            }
        });
    }

    private static void anchorPattern(Schema<?> property) {
        String pattern = property.getPattern();
        if (pattern != null && !pattern.startsWith("^")) {
            property.setPattern("^" + pattern + "$");
        }
    }

    private static void markAsSecret(String schemaName, Schema<?> property) {
        property.setFormat("password");
        property.setWriteOnly(true);
        if (!schemaName.endsWith(LOGIN_REQUEST_SUFFIX)) {
            property.setMinLength(MINIMUM_PASSWORD_LENGTH);
        }
    }
}
