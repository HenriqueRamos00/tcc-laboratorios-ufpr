package br.ufpr.tcc.auth.shared.database;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sonda de banco para a suite: executa SQL cru pela consulta nativa do JPA.
 *
 * Ela existe por dois motivos, e os dois valem a pena escrever.
 *
 * O primeiro e que a suite precisa semear e conferir linha **sem passar pelo mapeamento que
 * ela esta conferindo**. Fixture construida com a entidade da producao concorda com qualquer
 * erro de mapeamento, porque grava pelo mesmo caminho que depois le; e varios casos daqui
 * semeiam estado que a producao nao alcanca, como conta com e-mail normalizado em caixa
 * mista, token ja revogado e link com prazo no passado. Para isso SQL e a ferramenta certa, e
 * trocar por entidade seria perder cobertura em nome de uniformidade.
 *
 * O segundo e que o modulo passou a ter uma unica interface de persistencia. O DatabaseProbe que
 * estava aqui trazia o acesso direto ao DataSource de volta so para a suite, e o mesmo SQL
 * roda igual pela consulta nativa, que e JPA.
 *
 * A conversao de excecao e explicita, e usa o mesmo tradutor que o Spring usa quando a
 * traducao vem da anotacao de repositorio. Isso importa: existem casos que afirmam sobre a
 * recusa do banco comparando o tipo da excecao, e a diferenca entre restricao violada e falha
 * generica e justamente o que eles medem. Medido com o tradutor generico no lugar deste:
 * violacao de restricao chegava como falha generica de sistema, e cinco casos de restricao de
 * esquema ficavam vermelhos sem nenhum defeito no esquema.
 *
 * O limite transacional e um por comando, como era antes: a consulta nativa exige transacao
 * para gravar, e a suite espera que cada comando desta sonda ja esteja confirmado quando a
 * linha seguinte do teste roda.
 */
@Repository
public class DatabaseProbe {

    /**
     * O tradutor de excecao do provedor, que e o mesmo que a anotacao de repositorio usa.
     */
    private static final HibernateJpaDialect DIALECT = new HibernateJpaDialect();

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate transactions;

    DatabaseProbe(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    public Statement statement(String sql) {
        return new Statement(this, sql);
    }

    /** Comando em construcao, com os parametros nomeados que ele ainda vai receber. */
    public static final class Statement {

        private final DatabaseProbe probe;
        private final String sql;
        private final Map<String, Object> parameters = new LinkedHashMap<>();

        private Statement(DatabaseProbe probe, String sql) {
            this.probe = probe;
            this.sql = sql;
        }

        /** Aceita valor nulo de proposito: coluna que aceita ausencia e semeada com nulo. */
        public Statement with(String name, Object value) {
            parameters.put(name, value);
            return this;
        }

        public int run() {
            return probe.inTransaction(query -> query.executeUpdate(), sql, parameters);
        }

        /**
         * Contagem como numero inteiro longo.
         *
         * O Oracle devolve NUMBER para COUNT, que chega como BigDecimal, e por isso a
         * conversao e explicita em vez de um molde para Long.
         */
        public long count() {
            return ((Number) probe.inTransaction(query -> query.getSingleResult(), sql,
                    parameters)).longValue();
        }

        /**
         * Valor unico, convertido quando o tipo pedido e numerico.
         *
         * A conversao existe porque o Oracle devolve NUMBER para toda coluna numerica, e ele
         * chega como BigDecimal: pedir Integer e receber BigDecimal derrubava um caso com
         * molde invalido, e nao com afirmacao vermelha.
         */
        public <T> T value(Class<T> type) {
            Object result = probe.inTransaction(query -> query.getSingleResult(), sql, parameters);
            return type.cast(asRequestedNumber(type, result));
        }

        public <T> List<T> values(Class<T> type) {
            List<?> rows = probe.inTransaction(query -> query.getResultList(), sql, parameters);
            return rows.stream().map(type::cast).toList();
        }

        /** Converte o numero do banco para o tipo pedido, e deixa o resto como veio. */
        private static Object asRequestedNumber(Class<?> type, Object result) {
            if (!(result instanceof Number number)) {
                return result;
            }
            if (type == Integer.class) {
                return number.intValue();
            }
            if (type == Long.class) {
                return number.longValue();
            }
            if (type == BigDecimal.class) {
                return number instanceof BigDecimal exact ? exact
                        : BigDecimal.valueOf(number.doubleValue());
            }
            return result;
        }
    }

    /** O que um comando faz com a consulta nativa depois de os parametros entrarem. */
    private interface Terminal<T> {

        T apply(Query query);
    }

    private <T> T inTransaction(Terminal<T> terminal, String sql,
            Map<String, Object> parameters) {
        try {
            return transactions.execute(status -> {
                Query query = entityManager.createNativeQuery(sql);
                parameters.forEach(query::setParameter);
                return terminal.apply(query);
            });
        } catch (PersistenceException failure) {
            DataAccessException translated = DIALECT.translateExceptionIfPossible(failure);
            throw translated == null ? failure : translated;
        }
    }
}
