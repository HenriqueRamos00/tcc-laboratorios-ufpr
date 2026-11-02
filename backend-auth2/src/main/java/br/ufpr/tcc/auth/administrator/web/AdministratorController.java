package br.ufpr.tcc.auth.administrator.web;

import br.ufpr.tcc.auth.administrator.AdministratorDirectoryService;
import br.ufpr.tcc.auth.administrator.AdministratorLifecycle;
import br.ufpr.tcc.auth.administrator.AdministratorSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de administrador. Alcance: credencial administrativa, e so ela.
 *
 * O conflito de e-mail responde 409 aqui, e nao o 401 uniforme das rotas de cliente, porque
 * quem chama ja tem direito de ler a lista inteira de administradores. A resposta nao conta
 * nada que ele nao possa consultar na rota ao lado.
 */
@SecurityRequirement(name = "credencial-administrativa")
@RestController
class AdministratorController {

    private final AdministratorDirectoryService directory;
    private final AdministratorLifecycle lifecycle;

    AdministratorController(AdministratorDirectoryService directory,
            AdministratorLifecycle lifecycle) {
        this.directory = directory;
        this.lifecycle = lifecycle;
    }

    /**
     * O administrador nasce ativo, e a situacao nao entra no pedido. Quem cadastra decide
     * quem existe; desativar e operacao propria, na rota de atualizacao.
     */
    record RegisterAdministratorRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72)
            @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                    minLength = 8,
                    description = "Senha, preservando espacos: o servidor nao corta espaco nas pontas,\n"
                            + " nao muda a caixa e nao remove acentuacao, porque cada uma dessas trocas\n"
                            + " mudaria a credencial em silencio. O minimo de oito, mais maiuscula,\n"
                            + " minuscula e digito, e cobrado pelo caso de uso e nao por anotacao.")
            String password) {

        @Override
        public String toString() {
            return "RegisterAdministratorRequest[email=" + email + ", password=[OMITIDA]]";
        }
    }

    /** Senha ausente mantem a atual, e senha presente encerra as sessoes abertas. */
    record UpdateAdministratorRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull Boolean active,
            @Size(max = 72)
            @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                    minLength = 8,
                    description = "Senha, preservando espacos: o servidor nao corta espaco nas pontas,\n"
                            + " nao muda a caixa e nao remove acentuacao, porque cada uma dessas trocas\n"
                            + " mudaria a credencial em silencio. O minimo de oito, mais maiuscula,\n"
                            + " minuscula e digito, e cobrado pelo caso de uso e nao por anotacao.")
            String password) {

        @Override
        public String toString() {
            return "UpdateAdministratorRequest[email=" + email + ", password=[OMITIDA]]";
        }
    }

    @GetMapping("/api/admins")
    ResponseEntity<List<AdministratorSummary>> list() {
        return ResponseEntity.ok(directory.list());
    }

    @PostMapping("/api/admins")
    ResponseEntity<AdministratorSummary> register(
            @Valid @RequestBody RegisterAdministratorRequest request) {
        AdministratorLifecycle.Registration registration =
                lifecycle.register(request.name(), request.email(), request.password());
        return switch (registration.outcome()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(registration.administrator());
            case EMAIL_ALREADY_REGISTERED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case PASSWORD_REJECTED -> ResponseEntity.badRequest().build();
        };
    }

    @GetMapping("/api/admins/{id}")
    ResponseEntity<AdministratorSummary> read(@PathVariable String id) {
        return directory.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/api/admins/{id}")
    ResponseEntity<AdministratorSummary> update(@PathVariable String id,
            @Valid @RequestBody UpdateAdministratorRequest request) {
        AdministratorLifecycle.Change change = lifecycle.update(id, request.name(),
                request.email(), request.active(), request.password());
        return switch (change.outcome()) {
            case CHANGED -> ResponseEntity.ok(change.administrator());
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case EMAIL_ALREADY_REGISTERED, LAST_ACTIVE_ADMINISTRATOR ->
                    ResponseEntity.status(HttpStatus.CONFLICT).build();
            case PASSWORD_REJECTED -> ResponseEntity.badRequest().build();
        };
    }

    @DeleteMapping("/api/admins/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        return switch (lifecycle.delete(id)) {
            case REMOVED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case LAST_ACTIVE_ADMINISTRATOR -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }
}
