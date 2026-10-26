package br.ufpr.tcc.auth.shared.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import io.hypersistence.tsid.TSID;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.generator.EventTypeSets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TsidGeneratorTest {

    private static final long JAVASCRIPT_SAFE_INTEGER = 9_007_199_254_740_992L;
    private static final int GENERATIONS_THAT_OVERFLOW_THE_COUNTER = 200_000;
    private static final int NODE_IN_THE_TEST_FACTORY = 7;

    private final TsidGenerator generator = new TsidGenerator();

    private long nextIdentifier() {
        return (Long) generator.generate(null, null, null, null);
    }

    @Test
    @DisplayName("identificadores gerados em sequencia sao crescentes")
    void sequentialIdentifiersIncrease() {
        long first = nextIdentifier();
        long second = nextIdentifier();

        assertThat(second).isGreaterThan(first);
    }

    // Com dez bits de no sobram doze de contador, ou seja 4096 valores por
    // milissegundo. Um laco curto cabe dentro de um milissegundo e nunca
    // exercita o estouro do contador, que e o unico ponto onde o valor poderia
    // repetir.
    @Test
    @DisplayName("muitas geracoes nao repetem valor nem invertem a ordem")
    void manyGenerationsNeverRepeat() {
        Set<Long> seen = new HashSet<>();
        long previous = Long.MIN_VALUE;

        for (int index = 0; index < GENERATIONS_THAT_OVERFLOW_THE_COUNTER; index++) {
            long identifier = nextIdentifier();
            assertThat(identifier).isGreaterThan(previous);
            seen.add(identifier);
            previous = identifier;
        }

        assertThat(seen).hasSize(GENERATIONS_THAT_OVERFLOW_THE_COUNTER);
    }

    @Test
    @DisplayName("o identificador e positivo e cabe em long")
    void identifierIsPositiveAndFitsInLong() {
        long identifier = nextIdentifier();

        assertThat(identifier).isPositive();
        assertThat(identifier >>> 63).isZero();
        assertThat(Long.parseLong(Long.toString(identifier))).isEqualTo(identifier);
    }

    // Nao e fronteira teorica: o TSID de hoje nasce cerca de cem vezes acima do
    // limite que o JavaScript representa, entao todo identificador deste servico
    // ja esta na faixa que quebra no navegador se viajar como numero.
    @Test
    @DisplayName("todo identificador ja nasce acima do inteiro seguro do JavaScript")
    void identifierExceedsJavaScriptSafeInteger() {
        assertThat(nextIdentifier()).isGreaterThan(JAVASCRIPT_SAFE_INTEGER);
    }

    @Test
    @DisplayName("o no configurado na fabrica aparece dentro do valor gerado")
    void generatedIdentifierCarriesTheConfiguredNode() {
        TsidGenerator withFixedNode = new TsidGenerator(
                TSID.Factory.builder().withNode(NODE_IN_THE_TEST_FACTORY).withNodeBits(10).build());

        long identifier = (Long) withFixedNode.generate(null, null, null, null);

        assertThat((identifier >>> 12) & 0x3FF).isEqualTo(NODE_IN_THE_TEST_FACTORY);
    }

    // Trocar por um gerador que produz o valor durante a execucao do INSERT faz
    // o identificador so existir depois da gravacao, e nenhum outro caso desta
    // classe percebe a troca.
    @Test
    @DisplayName("o identificador nasce antes do INSERT e so no INSERT")
    void identifierIsAssignedOnlyOnInsert() {
        assertThat(generator.getEventTypes()).isEqualTo(EventTypeSets.INSERT_ONLY);
        assertThat(generator.generatedOnExecution()).isFalse();
    }
}
