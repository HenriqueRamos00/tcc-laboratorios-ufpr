package br.ufpr.tcc.backend_auth.infrastructure.config;

import br.ufpr.tcc.backend_auth.application.service.ConfiguracaoInicialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConfiguracaoInicialRunner {
    private final ConfiguracaoInicialService setup;
    private final String baseUrl;

    public ConfiguracaoInicialRunner(ConfiguracaoInicialService setup,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.setup = setup;
        this.baseUrl = baseUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void informarLink() {
        setup.url(baseUrl).ifPresent(url -> log.info("Crie o primeiro administrador deste serviço: {}", url));
    }
}
