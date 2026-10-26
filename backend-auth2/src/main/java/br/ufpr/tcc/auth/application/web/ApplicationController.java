package br.ufpr.tcc.auth.application.web;

import br.ufpr.tcc.auth.application.ApplicationRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de aplicacao e emissao de token, as duas operacoes que a documentacao de
 * integracao descreve.
 *
 * Alcance: so credencial administrativa. A cadeia ja cobra isso, e o controlador nao
 * repete a checagem, porque autorizacao espalhada em dois lugares diverge no dia em que
 * alguem mexe num deles.
 */
@RestController
class ApplicationController {

    private final ApplicationRegistry registry;

    ApplicationController(ApplicationRegistry registry) {
        this.registry = registry;
    }

    /**
     * O formato do codigo nao e preferencia nossa: ele e o mesmo que o consumidor valida na
     * inicializacao dele, e o mesmo que o banco cobra. Pedacos de letra minuscula e digito
     * ligados por hifen isolado, sem hifen no comeco, no fim ou dobrado.
     */
    record RegisterApplicationRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*",
                    message = "Codigo deve ter apenas letras minusculas, digitos e hifen isolado")
            String code) {
    }

    @PostMapping("/api/services")
    ResponseEntity<ApplicationRegistry.RegisteredApplicationView> register(
            @Valid @RequestBody RegisterApplicationRequest request) {
        ApplicationRegistry.Registration registration =
                registry.register(request.name(), request.code());
        return switch (registration.outcome()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(registration.application());
            case CODE_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    @GetMapping("/api/services")
    ResponseEntity<List<ApplicationRegistry.RegisteredApplicationView>> list() {
        return ResponseEntity.ok(registry.list());
    }

    @GetMapping("/api/services/{serviceId}")
    ResponseEntity<ApplicationRegistry.RegisteredApplicationView> read(
            @PathVariable String serviceId) {
        return identifierOf(serviceId).flatMap(registry::find)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record UpdateApplicationRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*",
                    message = "Codigo deve ter apenas letras minusculas, digitos e hifen isolado")
            String code,
            @NotNull Boolean active) {
    }

    /**
     * A situacao e obrigatoria, e isto corrige um defeito medido.
     *
     * Enquanto o campo era opcional e ausente valia verdadeiro, um pedido enviado para
     * corrigir o nome de uma aplicacao desativada a devolvia ao ar: quem desativou nao pediu
     * isso, e quem corrigiu o nome nao soube que reabriu o acesso. A situacao decide o acesso
     * da aplicacao no mesmo instante, entao ela nao muda por omissao.
     */
    @PutMapping("/api/services/{serviceId}")
    ResponseEntity<ApplicationRegistry.RegisteredApplicationView> update(
            @PathVariable String serviceId, @Valid @RequestBody UpdateApplicationRequest request) {
        Optional<Long> parsed = identifierOf(serviceId);
        if (parsed.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        long applicationId = parsed.get();
        return switch (registry.update(applicationId, request.name(), request.code(),
                request.active())) {
            case CHANGED -> registry.find(applicationId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
            case CODE_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }

    /** Excluir aplicacao e desativar: ver a justificativa no registro. */
    @DeleteMapping("/api/services/{serviceId}")
    ResponseEntity<Void> deactivate(@PathVariable String serviceId) {
        return identifierOf(serviceId).filter(registry::deactivate)
                .map(deactivated -> ResponseEntity.noContent().<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/services/{serviceId}/tokens")
    ResponseEntity<List<ApplicationRegistry.TokenView>> listTokens(
            @PathVariable String serviceId) {
        return identifierOf(serviceId)
                .filter(registry::exists)
                .map(applicationId -> ResponseEntity.ok(registry.listTokens(applicationId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping(
            "/api/services/{serviceId}/tokens/{tokenId}")
    ResponseEntity<Void> revokeToken(@PathVariable String serviceId, @PathVariable String tokenId) {
        return identifierOf(serviceId)
                .flatMap(applicationId -> identifierOf(tokenId)
                        .filter(token -> registry.revokeToken(applicationId, token)))
                .map(revoked -> ResponseEntity.noContent().<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Identificador que nao e numero e identificador que nao existe sao o mesmo caso. */
    private static Optional<Long> identifierOf(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
        }
    }

    record IssueTokenRequest(
            @NotBlank @Size(max = 150) String name,
            @Positive Long expiresIn) {
    }

    @PostMapping("/api/services/{serviceId}/tokens")
    ResponseEntity<Object> issueToken(@PathVariable String serviceId,
            @Valid @RequestBody IssueTokenRequest request) {
        Optional<Long> identifier = identifierOf(serviceId).filter(registry::exists);
        if (identifier.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        long applicationId = identifier.get();
        ApplicationRegistry.IssuedTokenView issued = registry.issueToken(applicationId,
                request.name(),
                request.expiresIn() == null ? null : Duration.ofSeconds(request.expiresIn()));

        Map<String, Object> token = new LinkedHashMap<>();
        token.put("id", issued.id());
        token.put("name", issued.name());
        token.put("serviceId", issued.serviceId());
        token.put("createdAt", issued.createdAt().toString());
        token.put("expiresAt", issued.expiresAt() == null ? null : issued.expiresAt().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", issued.accessToken());
        body.put("tokenType", "Bearer");
        body.put("token", token);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
