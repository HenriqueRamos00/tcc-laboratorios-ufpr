package br.ufpr.tcc.auth.administrator.web;

import br.ufpr.tcc.auth.administrator.AdministratorAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada do administrador.
 *
 * O 401 daqui sai sem desafio, como o do login de cliente e pela mesma razao: ele e falha
 * de credencial de pessoa, nao de integracao.
 */
@RestController
class AdministratorLoginController {

    private final AdministratorAccess access;

    AdministratorLoginController(AdministratorAccess access) {
        this.access = access;
    }

    record AdministratorLoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {

        @Override
        public String toString() {
            return "AdministratorLoginRequest[email=" + email + ", password=[OMITIDA]]";
        }
    }

    @PostMapping("/api/admin/auth/login")
    ResponseEntity<Object> login(@Valid @RequestBody AdministratorLoginRequest request) {
        return access.signIn(request.email(), request.password())
                .<ResponseEntity<Object>>map(session -> ResponseEntity.ok(Map.of(
                        "accessToken", session.accessToken(),
                        "tokenType", "Bearer",
                        "expiresIn", session.expiresInSeconds(),
                        "user", Map.of("id", session.id(), "name", session.name(),
                                "email", session.email()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
