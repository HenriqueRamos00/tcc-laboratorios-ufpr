package br.ufpr.tcc.auth.shared.identifier;

import io.hypersistence.tsid.TSID;
import org.springframework.stereotype.Component;

/**
 * Produz identificador para o codigo que grava sem passar pelo Hibernate.
 *
 * A fabrica e a mesma que o gerador de entidade usa, para que os dois caminhos nao
 * produzam identificadores com propriedades diferentes.
 */
@Component
public class IdentifierFactory {

    private final TSID.Factory factory = TSID.Factory.builder().build();

    public long nextIdentifier() {
        return factory.generate().toLong();
    }
}
