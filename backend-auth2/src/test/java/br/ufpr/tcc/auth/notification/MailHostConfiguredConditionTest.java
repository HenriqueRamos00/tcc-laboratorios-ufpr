package br.ufpr.tcc.auth.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

class MailHostConfiguredConditionTest {

    private final MailHostConfiguredCondition condition = new MailHostConfiguredCondition();
    private final AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);

    @Test
    void hostVazioMantemTransporteDesabilitado() {
        ConditionContext context = contextoComHost("");

        assertThat(condition.matches(context, metadata)).isFalse();
    }

    @Test
    void hostConfiguradoHabilitaTransporte() {
        ConditionContext context = contextoComHost("smtp.exemplo");

        assertThat(condition.matches(context, metadata)).isTrue();
    }

    private static ConditionContext contextoComHost(String host) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(
                new MockEnvironment().withProperty("spring.mail.host", host));
        return context;
    }
}
