package br.ufpr.tcc.auth.company.web;

import br.ufpr.tcc.auth.company.CompanyRegistry;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de empresa. Alcance: credencial administrativa, e so ela.
 *
 * Quem gerencia empresa e o portal administrativo. A cadeia ja cobra a credencial, e o
 * controlador nao repete a checagem, porque autorizacao espalhada em dois lugares diverge
 * no dia em que alguem mexe num deles.
 */
@SecurityRequirement(name = "credencial-administrativa")
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
    ResponseEntity<List<CompanyRegistry.CompanyView>> list() {
        return ResponseEntity.ok(registry.list());
    }

    @PostMapping("/api/companies")
    ResponseEntity<CompanyRegistry.CompanyView> register(
            @Valid @RequestBody CompanyRequest request) {
        CompanyRegistry.Registration registration =
                registry.register(request.name(), request.cnpj());
        return switch (registration.outcome()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(registration.company());
            case CNPJ_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    @GetMapping("/api/companies/{id}")
    ResponseEntity<CompanyRegistry.CompanyView> read(@PathVariable String id) {
        return identifierOf(id).flatMap(registry::find)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/companies/{id}")
    ResponseEntity<CompanyRegistry.CompanyView> update(@PathVariable String id,
            @Valid @RequestBody CompanyRequest request) {
        Optional<Long> companyId = identifierOf(id);
        if (companyId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return switch (registry.update(companyId.get(), request.name(), request.cnpj())) {
            case CHANGED -> registry.find(companyId.get())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case CNPJ_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    /** Empresa que alguma conta ainda representa responde conflito, e nao e removida. */
    @DeleteMapping("/api/companies/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        Optional<Long> companyId = identifierOf(id);
        if (companyId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return switch (registry.delete(companyId.get())) {
            case REMOVED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case STILL_LINKED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
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
