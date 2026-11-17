package br.ufpr.tcc.auth.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoSeedDisabledTest {

    @Test
    void seedIsAbsentUnlessExplicitlyEnabled() {
        new ApplicationContextRunner().withUserConfiguration(DemoSeedRunner.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DemoSeedRunner.class));
    }
}
