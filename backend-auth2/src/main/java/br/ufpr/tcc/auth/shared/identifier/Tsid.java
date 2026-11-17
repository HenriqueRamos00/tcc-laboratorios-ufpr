package br.ufpr.tcc.auth.shared.identifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/**
 * Marca a chave primaria que recebe um TSID atribuido pelo Hibernate antes do INSERT.
 *
 * O valor nao e preenchido no construtor da entidade de proposito: instancia nova que ja
 * chega com identificador faz o save() do Spring Data trata-la como registro existente e
 * cair em merge(), que emite um SELECT antes de cada INSERT.
 */
@IdGeneratorType(TsidGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Tsid {
}
