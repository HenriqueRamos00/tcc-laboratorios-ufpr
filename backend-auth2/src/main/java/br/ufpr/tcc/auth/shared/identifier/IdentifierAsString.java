package br.ufpr.tcc.auth.shared.identifier;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Faz o campo viajar como string no JSON, nos dois sentidos.
 *
 * Aplicada no campo, e nao registrada como modulo, de proposito: um modulo que mapeasse
 * Long para string atingiria todo numero de 64 bits de todo DTO, e contador e prazo em
 * segundos precisam continuar numero para o consumidor.
 */
@JacksonAnnotationsInside
@JsonSerialize(using = IdentifierSerializer.class)
@JsonDeserialize(using = IdentifierDeserializer.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT})
public @interface IdentifierAsString {
}
