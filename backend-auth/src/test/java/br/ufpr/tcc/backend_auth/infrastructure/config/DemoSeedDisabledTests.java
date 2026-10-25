package br.ufpr.tcc.backend_auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth_without_demo;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
class DemoSeedDisabledTests {
    @Autowired ApplicationContext context;
    @Autowired UsuarioRepository usuarios;

    @Test
    void seedIsAbsentUnlessExplicitlyEnabled() {
        assertThat(context.getBeansOfType(DemoSeedRunner.class)).isEmpty();
        assertThat(usuarios.count()).isZero();
    }
}
