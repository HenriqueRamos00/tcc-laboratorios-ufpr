package br.ufpr.tcc.auth.shared.identifier;

import io.hypersistence.tsid.TSID;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

/**
 * Produz o identificador do registro antes do INSERT.
 *
 * O no e lido pela propria biblioteca, da propriedade de sistema tsid.node ou da variavel
 * de ambiente TSID_NODE, e a quantidade de nos de tsid.node.count ou TSID_NODE_COUNT. Sem
 * nenhuma das duas a biblioteca reserva dez bits e sorteia um no entre 1024 na construcao
 * da fabrica, entao duas instancias podem receber o mesmo no e repetir valor dentro do
 * mesmo milissegundo. Declarar a quantidade sem declarar o indice e pior que nao declarar
 * nada, porque reduz o espaco do sorteio.
 */
public class TsidGenerator implements BeforeExecutionGenerator {

    private static final long serialVersionUID = 1L;

    private final TSID.Factory factory;

    public TsidGenerator() {
        this(TSID.Factory.builder().build());
    }

    /**
     * Construtor de teste, que recebe uma fabrica com no fixo para o teste poder afirmar
     * sobre os bits do no sem mexer em propriedade de sistema.
     */
    TsidGenerator(TSID.Factory factory) {
        this.factory = factory;
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
            Object currentValue, EventType eventType) {
        return factory.generate().toLong();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
