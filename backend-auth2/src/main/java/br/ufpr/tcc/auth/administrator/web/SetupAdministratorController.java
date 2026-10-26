package br.ufpr.tcc.auth.administrator.web;

import br.ufpr.tcc.auth.administrator.AdministratorAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro do primeiro administrador.
 *
 * A rota responde como inexistente sempre que qualquer uma das duas condicoes falha, sem
 * dizer qual: contar que o segredo estava errado confirmaria que a instalacao ainda nao
 * foi feita, e contar que ja existe administrador confirmaria que foi.
 */
@RestController
class SetupAdministratorController {

    static final String SETUP_SECRET_HEADER = "X-Setup-Secret";

    private final AdministratorAccess access;

    SetupAdministratorController(AdministratorAccess access) {
        this.access = access;
    }

    record SetupRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {

        @Override
        public String toString() {
            return "SetupRequest[email=" + email + ", password=[OMITIDA]]";
        }
    }

    @PostMapping("/api/setup/admin")
    ResponseEntity<Void> setup(
            @RequestHeader(value = SETUP_SECRET_HEADER, required = false) String presentedSecret,
            @Valid @RequestBody SetupRequest request) {
        return switch (access.setupFirstAdministrator(
                presentedSecret, request.name(), request.email(), request.password())) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).build();
            case PASSWORD_REJECTED -> ResponseEntity.badRequest().build();
            case ROUTE_DOES_NOT_EXIST -> ResponseEntity.notFound().build();
        };
    }
}
