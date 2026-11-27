package br.ufpr.tcc.auth.account.web;

import br.ufpr.tcc.auth.account.usecase.ConfirmEmailUseCase;
import br.ufpr.tcc.auth.account.usecase.RequestPasswordRecoveryUseCase;
import br.ufpr.tcc.auth.account.usecase.ResetPasswordUseCase;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas dos links enviados por e-mail: confirmacao de cadastro e recuperacao de senha. A
 * SecurityConfiguration exige credencial de aplicacao, e o escopo sai dela: link de uma
 * aplicacao nao e consumido por outra.
 */
@RestController
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AccountTokenController {

    private static final String APPLICATION = "credencial-de-aplicacao";

    private final ConfirmEmailUseCase confirmEmailUseCase;
    private final RequestPasswordRecoveryUseCase requestPasswordRecoveryUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    record ConfirmationRequest(@NotBlank String token) {

        @Override
        public String toString() {
            return "ConfirmationRequest[token=[OMITIDO]]";
        }
    }

    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/auth/confirmations")
    ResponseEntity<Void> confirm(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody ConfirmationRequest request) {
        return confirmEmailUseCase.execute(caller, request.token())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }

    record RecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    /** Responde sempre 202, exista ou nao o endereco. */
    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/auth/recoveries")
    ResponseEntity<Void> requestRecovery(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody RecoveryRequest request) {
        requestPasswordRecoveryUseCase.execute(caller, request.email());
        return ResponseEntity.accepted().build();
    }

    record PasswordResetRequest(@NotBlank String token, @NotBlank @Size(max = 72)
            @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                    minLength = 8,
                    description = "Senha, preservando espacos: o servidor nao corta espaco nas pontas,\n"
                            + " nao muda a caixa e nao remove acentuacao, porque cada uma dessas trocas\n"
                            + " mudaria a credencial em silencio. O minimo de oito, mais maiuscula,\n"
                            + " minuscula e digito, e cobrado pelo caso de uso e nao por anotacao.")
            String password) {

        @Override
        public String toString() {
            return "PasswordResetRequest[token=[OMITIDO], password=[OMITIDA]]";
        }
    }

    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/auth/password-resets")
    ResponseEntity<Void> resetPassword(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody PasswordResetRequest request) {
        return resetPasswordUseCase.execute(caller, request.token(), request.password())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }
}
