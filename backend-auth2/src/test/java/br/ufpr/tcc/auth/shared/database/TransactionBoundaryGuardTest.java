package br.ufpr.tcc.auth.shared.database;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.account.AccountLifecycle;
import br.ufpr.tcc.auth.administrator.AdministratorLifecycle;
import br.ufpr.tcc.auth.application.ApplicationRegistry;
import br.ufpr.tcc.auth.company.CompanyRegistry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guarda estrutural do limite transacional.
 *
 * Ela nasceu de um defeito medido: em `ApplicationRegistry` a anotacao `@Transactional`
 * tinha caido sobre um `record` e sobre um `enum` aninhados, e nao sobre os metodos logo
 * abaixo deles. Tipo aninhado que nao e bean nao e proxiado, entao a anotacao nao tinha
 * efeito nenhum e os dois metodos rodavam em auto commit. O lado que recusa anotacao em tipo
 * aninhado continua aqui, porque e a forma daquele defeito.
 *
 * O outro lado mudou de sinal na troca para JPA, e o motivo e de semantica. Quando a
 * gravacao falha por violacao de restricao, o provedor **marca aquela transacao para
 * desfazer**; devolver um desfecho normal de dentro dela faz o commit falhar depois, com erro
 * de servidor, em cima de um caso que o contrato descreve como conflito. Entao os metodos que
 * traduzem violacao de restricao para conflito **nao podem** ser o proprio limite
 * transacional: eles abrem a transacao pelo modelo e tratam a excecao de fora dela.
 *
 * Por isso a guarda cobra duas coisas desses metodos: que eles nao tenham atributo
 * transacional resolvido, que e o que faz o tratamento ficar do lado de fora, e que a classe
 * declare o modelo transacional, que e o que prova que o limite existe em algum lugar. Sem o
 * segundo lado, apagar o limite inteiro deixaria esta guarda verde.
 *
 * Quem prova o comportamento sao os casos de corrida, que chamam as mesmas rotas em paralelo
 * e exigem 409 e nao 500. Esta guarda protege contra a regressao estrutural que aqueles casos
 * levariam tempo para acusar.
 */
class TransactionBoundaryGuardTest {

    private static final String MODULE_PACKAGE = "br.ufpr.tcc.auth";

    private final TransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

    @Test
    @DisplayName("nenhum tipo aninhado de bean carrega @Transactional")
    void noNestedTypeOfABeanCarriesTransactional() throws Exception {
        List<String> misplaced = new ArrayList<>();
        for (Class<?> bean : moduleBeans()) {
            for (Class<?> nested : bean.getDeclaredClasses()) {
                if (nested.isAnnotationPresent(Transactional.class)) {
                    misplaced.add(bean.getSimpleName() + "." + nested.getSimpleName());
                }
            }
        }

        assertThat(misplaced)
                .as("a anotacao vale para o tipo logo abaixo dela, nao para o metodo seguinte")
                .isEmpty();
    }

    @Test
    @DisplayName("quem traduz violacao de restricao abre a transacao pelo modelo, e nao pela anotacao")
    void outcomesThatTranslateConstraintViolationsOwnTheirTransaction() throws Exception {
        List<Method> translators = List.of(
                ApplicationRegistry.class.getMethod("register", String.class, String.class),
                ApplicationRegistry.class.getMethod("update", long.class, String.class,
                        String.class, boolean.class),
                CompanyRegistry.class.getMethod("register", long.class, String.class, String.class),
                CompanyRegistry.class.getMethod("update", long.class, long.class, String.class,
                        String.class),
                AccountLifecycle.class.getMethod("register",
                        br.ufpr.tcc.auth.shared.security.ApplicationCaller.class, String.class,
                        String.class, String.class, String.class),
                AccountLifecycle.class.getMethod("requestPasswordRecovery",
                        br.ufpr.tcc.auth.shared.security.ApplicationCaller.class, String.class),
                AdministratorLifecycle.class.getMethod("register", String.class, String.class,
                        String.class),
                AdministratorLifecycle.class.getMethod("update", String.class, String.class,
                        String.class, boolean.class, String.class));

        List<String> annotated = new ArrayList<>();
        List<String> withoutTheTemplate = new ArrayList<>();
        for (Method translator : translators) {
            String name = translator.getDeclaringClass().getSimpleName() + "." + translator.getName();
            if (attributes.getTransactionAttribute(translator, translator.getDeclaringClass()) != null) {
                annotated.add(name);
            }
            if (!declaresTheTransactionTemplate(translator.getDeclaringClass())) {
                withoutTheTemplate.add(name);
            }
        }

        assertThat(annotated)
                .as("tratar violacao de restricao dentro da transacao marcada para desfazer"
                        + " devolve erro de servidor no lugar do conflito")
                .isEmpty();
        assertThat(withoutTheTemplate)
                .as("sem o modelo transacional nao existe limite nenhum, e nao apenas limite"
                        + " declarado noutro lugar")
                .isEmpty();
    }

    private static boolean declaresTheTransactionTemplate(Class<?> bean) {
        return java.util.Arrays.stream(bean.getDeclaredFields())
                .anyMatch(field -> field.getType() == TransactionTemplate.class);
    }

    private static List<Class<?>> moduleBeans() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<Class<?>> beans = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(MODULE_PACKAGE)) {
            beans.add(Class.forName(definition.getBeanClassName()));
        }
        return beans;
    }
}
