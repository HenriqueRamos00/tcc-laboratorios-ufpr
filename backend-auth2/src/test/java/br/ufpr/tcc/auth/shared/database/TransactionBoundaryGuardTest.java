package br.ufpr.tcc.auth.shared.database;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.application.ApplicationRegistry;
import br.ufpr.tcc.auth.company.CompanyRegistry;
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

/**
 * Guarda estrutural do limite transacional.
 *
 * Ela existe por um defeito medido: em `ApplicationRegistry` a anotacao `@Transactional`
 * tinha caido sobre um `record` e sobre um `enum` aninhados, e nao sobre os metodos logo
 * abaixo deles. Tipo aninhado que nao e bean nao e proxiado, entao a anotacao nao tinha
 * efeito nenhum e os dois metodos rodavam em auto commit. Nada quebrava hoje, porque nenhum
 * dos dois fazia duas escritas; o problema e que o limite declarado nao existia, e a segunda
 * escrita que alguem acrescentasse deixaria de ser atomica em silencio.
 *
 * Por isso a guarda tem dois lados. Um recusa a anotacao em tipo aninhado, que e a forma do
 * defeito. O outro cobra que os metodos que sofreram com ele tenham de fato atributo
 * transacional resolvido, que e o que prova a correcao e nao apenas a ausencia do erro.
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
    @DisplayName("cadastro e alteracao rodam dentro de transacao, nos dois cadastros com desfecho")
    void registeringAndUpdatingRunInsideATransaction() throws Exception {
        assertThat(attributes.getTransactionAttribute(
                ApplicationRegistry.class.getMethod("register", String.class, String.class),
                ApplicationRegistry.class)).as("cadastro de aplicacao").isNotNull();
        assertThat(attributes.getTransactionAttribute(
                ApplicationRegistry.class.getMethod("update", long.class, String.class,
                        String.class, boolean.class),
                ApplicationRegistry.class)).as("alteracao de aplicacao").isNotNull();
        assertThat(attributes.getTransactionAttribute(
                CompanyRegistry.class.getMethod("register", long.class, String.class, String.class),
                CompanyRegistry.class)).as("cadastro de empresa").isNotNull();
        assertThat(attributes.getTransactionAttribute(
                CompanyRegistry.class.getMethod("update", long.class, long.class, String.class,
                        String.class),
                CompanyRegistry.class)).as("alteracao de empresa").isNotNull();
    }

    private List<Class<?>> moduleBeans() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<Class<?>> beans = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(MODULE_PACKAGE)) {
            beans.add(Class.forName(definition.getBeanClassName()));
        }
        assertThat(beans).as("a varredura precisa achar os beans do modulo").isNotEmpty();
        return beans;
    }
}
