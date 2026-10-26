package br.ufpr.tcc.auth.account.web;

import br.ufpr.tcc.auth.account.AccountLifecycle;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import br.ufpr.tcc.auth.shared.security.CallerScope;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de cliente e confirmacao do endereco de e-mail. */
@RestController
class ClientAccountController {

    private final AccountLifecycle lifecycle;

    ClientAccountController(AccountLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    record CreateClientRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password,
            @Pattern(regexp = "CLIENTE", message = "Perfil deve ser CLIENTE") String role,
            String serviceId) {

        @Override
        public String toString() {
            return "CreateClientRequest[email=" + email + ", password=[OMITIDA]]";
        }
    }

    @PostMapping("/api/users")
    ResponseEntity<Void> create(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody CreateClientRequest request) {
        return switch (lifecycle.register(caller, request.serviceId(), request.name(),
                request.email(), request.password())) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).build();
            case EMAIL_TAKEN -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case PASSWORD_REJECTED, SCOPE_REJECTED -> ResponseEntity.badRequest().build();
        };
    }

    record ConfirmationRequest(@NotBlank String token) {

        @Override
        public String toString() {
            return "ConfirmationRequest[token=[OMITIDO]]";
        }
    }

    /**
     * Listagem de contas no alcance de quem chamou.
     *
     * Aplicacao enxerga as contas dela; administrador atravessa aplicacoes. O filtro vai
     * na consulta e nunca na memoria depois: consultar tudo e descartar devolveria o mesmo
     * corpo e traria dado alheio para dentro do processo.
     */
    @GetMapping("/api/users")
    ResponseEntity<List<AccountLifecycle.AccountSummary>> list(
            @AuthenticationPrincipal Object caller) {
        return ResponseEntity.ok(lifecycle.list(CallerScope.applicationScopeOf(caller)));
    }

    @GetMapping("/api/users/{id}")
    ResponseEntity<AccountLifecycle.AccountSummary> read(@AuthenticationPrincipal Object caller,
            @PathVariable String id) {
        return lifecycle.find(CallerScope.applicationScopeOf(caller), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record UpdateClientRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = "CLIENTE", message = "Perfil deve ser CLIENTE") String role,
            Boolean active,
            String serviceId) {
    }

    @PutMapping("/api/users/{id}")
    ResponseEntity<AccountLifecycle.AccountSummary> update(@AuthenticationPrincipal Object caller,
            @PathVariable String id, @Valid @RequestBody UpdateClientRequest request) {
        return lifecycle.update(CallerScope.applicationScopeOf(caller), id, request.name(),
                        request.active() == null || request.active())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Recurso de outra aplicacao responde igual a identificador inventado. */
    @DeleteMapping("/api/users/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Object caller, @PathVariable String id) {
        return lifecycle.delete(CallerScope.applicationScopeOf(caller), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    record RecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    /** Responde sempre 202, exista ou nao o endereco. */
    @PostMapping("/api/auth/recoveries")
    ResponseEntity<Void> requestRecovery(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody RecoveryRequest request) {
        lifecycle.requestPasswordRecovery(caller, request.email());
        return ResponseEntity.accepted().build();
    }

    record PasswordResetRequest(@NotBlank String token, @NotBlank @Size(max = 72) String password) {

        @Override
        public String toString() {
            return "PasswordResetRequest[token=[OMITIDO], password=[OMITIDA]]";
        }
    }

    @PostMapping("/api/auth/password-resets")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return lifecycle.resetPassword(request.token(), request.password())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }

    @PostMapping("/api/auth/confirmations")
    ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmationRequest request) {
        return lifecycle.confirmEmail(request.token())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }
}
