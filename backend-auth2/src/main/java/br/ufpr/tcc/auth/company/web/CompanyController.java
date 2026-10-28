package br.ufpr.tcc.auth.company.web;

import br.ufpr.tcc.auth.company.CompanyRegistry;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de empresa. O alcance vem da credencial, e empresa de outra aplicacao responde
 * como inexistente.
 */
@SecurityRequirement(name = "credencial-de-aplicacao")
@RestController
class CompanyController {

    private final CompanyRegistry registry;

    CompanyController(CompanyRegistry registry) {
        this.registry = registry;
    }

    /**
     * Somente digito, sem mascara. O banco cobra a mesma coisa, e guardar com pontuacao
     * faria o mesmo CNPJ entrar duas vezes escrito de formas diferentes.
     */
    record CompanyRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "[0-9]{14}",
                    message = "CNPJ deve ter 14 digitos, sem pontuacao")
            @Schema(pattern = "^[0-9]{14}$")
            String cnpj) {
    }

    @GetMapping("/api/companies")
    ResponseEntity<List<CompanyRegistry.CompanyView>> list(
            @AuthenticationPrincipal ApplicationCaller caller) {
        return ResponseEntity.ok(registry.list(caller.applicationId()));
    }

    @PostMapping("/api/companies")
    ResponseEntity<CompanyRegistry.CompanyView> register(
            @AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody CompanyRequest request) {
        CompanyRegistry.Registration registration =
                registry.register(caller.applicationId(), request.name(), request.cnpj());
        return switch (registration.outcome()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(registration.company());
            case CNPJ_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    @GetMapping("/api/companies/{id}")
    ResponseEntity<CompanyRegistry.CompanyView> read(
            @AuthenticationPrincipal ApplicationCaller caller, @PathVariable String id) {
        return identifierOf(id).flatMap(companyId -> registry.find(caller.applicationId(), companyId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/companies/{id}")
    ResponseEntity<CompanyRegistry.CompanyView> update(
            @AuthenticationPrincipal ApplicationCaller caller, @PathVariable String id,
            @Valid @RequestBody CompanyRequest request) {
        Optional<Long> companyId = identifierOf(id);
        if (companyId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CompanyRegistry.UpdateOutcome outcome = registry.update(caller.applicationId(),
                companyId.get(), request.name(), request.cnpj());
        return switch (outcome) {
            case CHANGED -> registry.find(caller.applicationId(), companyId.get())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CNPJ_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    @DeleteMapping("/api/companies/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal ApplicationCaller caller,
            @PathVariable String id) {
        return identifierOf(id).filter(companyId -> registry.delete(caller.applicationId(), companyId))
                .map(removed -> ResponseEntity.noContent().<Void>build())
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
}
