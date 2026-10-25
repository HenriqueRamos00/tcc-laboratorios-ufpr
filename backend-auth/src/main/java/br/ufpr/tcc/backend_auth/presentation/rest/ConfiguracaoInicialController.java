package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarAdministradorRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorResponse;
import br.ufpr.tcc.backend_auth.application.service.ConfiguracaoInicialService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Configuração inicial")
public class ConfiguracaoInicialController {
    private final ConfiguracaoInicialService setup;

    @GetMapping("/setup")
    @Hidden
    public ResponseEntity<Resource> formulario(@RequestParam(required = false) String token) {
        setup.validarAcesso(token);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(new ClassPathResource("setup/admin.html"));
    }

    @PostMapping("/api/setup/admin")
    @Operation(summary = "Cadastrar o primeiro administrador", description = "Use Authorize com setupToken, copiando o parâmetro token do link exibido nos logs. Após o primeiro cadastro, retorna 410.")
    @SecurityRequirement(name = "setupToken")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Primeiro administrador cadastrado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/InvalidSetupLink"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/EmailConflict"),
        @ApiResponse(responseCode = "410", ref = "#/components/responses/SetupCompleted")
    })
    public ResponseEntity<AdministradorResponse> registrar(
            @Parameter(hidden = true) @RequestHeader(value = "X-Setup-Token", required = false) String token,
            @Valid @RequestBody CriarAdministradorRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(setup.concluir(token, request));
    }
}
