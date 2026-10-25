package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.response.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Saúde")
public class HealthController {
    @GetMapping("/health")
    @Operation(summary = "Verificar disponibilidade HTTP do serviço")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
