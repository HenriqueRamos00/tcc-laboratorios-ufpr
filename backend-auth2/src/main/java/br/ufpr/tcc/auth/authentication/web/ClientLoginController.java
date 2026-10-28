package br.ufpr.tcc.auth.authentication.web;

import br.ufpr.tcc.auth.authentication.ClientAuthenticationFacade;
import br.ufpr.tcc.auth.authentication.LoginDecision;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login de um cliente de uma aplicacao.
 *
 * A aplicacao chamadora vem da credencial, pelo principal que a cadeia publicou, e nunca
 * do corpo: identificador de aplicacao que chegasse no JSON seria pedido do chamador, nao
 * identidade.
 *
 * O 401 daqui sai sem desafio de proposito, e isso e contrato medido no consumidor: com o
 * desafio, senha errada apareceria na tela como falha de infraestrutura.
 */
@SecurityRequirement(name = "credencial-de-aplicacao")
@RestController
class ClientLoginController {

    private final ClientAuthenticationFacade authentication;

    ClientLoginController(ClientAuthenticationFacade authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<Object> login(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody ClientLoginRequest request) {
        LoginDecision decision =
                authentication.authenticate(caller, request.email(), request.password());
        if (decision.authenticated()) {
            return ResponseEntity.ok(decision.identity());
        }
        if (decision.locked()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()))
                    .build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
