package br.ufpr.tcc.auth.account.web;

import br.ufpr.tcc.auth.account.usecase.CancelEmailChangeUseCase;
import br.ufpr.tcc.auth.account.usecase.ChangePasswordUseCase;
import br.ufpr.tcc.auth.account.usecase.ConfirmEmailChangeUseCase;
import br.ufpr.tcc.auth.account.usecase.GetSelfServiceProfileUseCase;
import br.ufpr.tcc.auth.account.usecase.GetSessionStateUseCase;
import br.ufpr.tcc.auth.account.usecase.RequestEmailChangeUseCase;
import br.ufpr.tcc.auth.account.usecase.SelfServiceProfile;
import br.ufpr.tcc.auth.account.usecase.UpdateSelfServiceProfileUseCase;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import br.ufpr.tcc.auth.shared.web.ProblemDetailWriter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas de autoatendimento em /api/self-service, restritas a conta da aplicacao autenticada.
 * Cada rota chama um caso de uso e traduz o desfecho dele em resposta HTTP.
 */
@RestController
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ClientSelfServiceController {

    private static final String APPLICATION = "credencial-de-aplicacao";
    private static final String NO_STORE = "no-store";
    private static final String ACCOUNT_ID = "[0-9]{1,19}";

    private final GetSelfServiceProfileUseCase getSelfServiceProfileUseCase;
    private final UpdateSelfServiceProfileUseCase updateSelfServiceProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final RequestEmailChangeUseCase requestEmailChangeUseCase;
    private final CancelEmailChangeUseCase cancelEmailChangeUseCase;
    private final ConfirmEmailChangeUseCase confirmEmailChangeUseCase;
    private final GetSessionStateUseCase getSessionStateUseCase;
    private final ProblemDetailWriter problemDetailWriter;

    @SecurityRequirement(name = APPLICATION)
    @GetMapping("/api/self-service/accounts/{accountId:" + ACCOUNT_ID + "}/profile")
    ResponseEntity<SelfServiceProfile> profile(@AuthenticationPrincipal ApplicationCaller caller,
            @PathVariable String accountId) {
        return getSelfServiceProfileUseCase.execute(caller, accountId)
                .map(profile -> ResponseEntity.ok().header("Cache-Control", NO_STORE).body(profile))
                .orElseGet(() -> ResponseEntity.notFound().header("Cache-Control", NO_STORE).build());
    }

    static final class ProfilePatchRequest {
        @NotBlank
        @Size(max = 150)
        private String name;

        @JsonIgnore
        private final Set<String> unsupportedFields = new LinkedHashSet<>();

        String name() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonAnySetter
        public void rejectUnsupportedField(String name, Object value) {
            unsupportedFields.add(name);
        }

        boolean hasUnsupportedFields() {
            return !unsupportedFields.isEmpty();
        }
    }

    @SecurityRequirement(name = APPLICATION)
    @PatchMapping("/api/self-service/accounts/{accountId:" + ACCOUNT_ID + "}/profile")
    ResponseEntity<?> updateProfile(@AuthenticationPrincipal ApplicationCaller caller,
            @PathVariable String accountId, @Valid @RequestBody ProfilePatchRequest request) {
        if (request.hasUnsupportedFields()) {
            return error(HttpStatus.BAD_REQUEST, "invalid-profile", "Dados de perfil invalidos");
        }
        return updateSelfServiceProfileUseCase.execute(caller, accountId, request.name())
                .<ResponseEntity<?>>map(profile -> ResponseEntity.ok()
                        .header("Cache-Control", NO_STORE).body(profile))
                .orElseGet(() -> ResponseEntity.notFound()
                        .header("Cache-Control", NO_STORE).build());
    }

    record EmailChangeRequest(@NotBlank @Email @Size(max = 254) String newEmail,
            @NotBlank String currentPassword) {
        @Override
        public String toString() {
            return "EmailChangeRequest[newEmail=[OMITIDO], currentPassword=[OMITIDA]]";
        }
    }

    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/self-service/accounts/{accountId:" + ACCOUNT_ID + "}/email-change")
    ResponseEntity<?> requestEmailChange(@AuthenticationPrincipal ApplicationCaller caller,
            @PathVariable String accountId, @Valid @RequestBody EmailChangeRequest request) {
        RequestEmailChangeUseCase.Result result = requestEmailChangeUseCase.execute(caller,
                accountId, request.newEmail(), request.currentPassword());
        return switch (result.outcome()) {
            case ACCEPTED -> ResponseEntity.accepted().header("Cache-Control", NO_STORE)
                    .body(result.pending());
            case EMAIL_DELIVERY_UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE,
                    "email-delivery-unavailable", "Servico de e-mail indisponivel");
            case NOT_FOUND -> ResponseEntity.notFound().header("Cache-Control", NO_STORE).build();
            case CURRENT_PASSWORD_INCORRECT -> error(HttpStatus.BAD_REQUEST,
                    "current-password-incorrect", "Senha atual incorreta");
            case EMAIL_ALREADY_IN_USE -> error(HttpStatus.CONFLICT,
                    "email-already-in-use", "Endereco de e-mail ja esta em uso");
            case EMAIL_CHANGE_CONFLICT -> error(HttpStatus.CONFLICT,
                    "email-change-conflict", "Solicitacao de alteracao de e-mail incompativel");
            case RATE_LIMITED -> rateLimited(result.retryAfterSeconds());
        };
    }

    @SecurityRequirement(name = APPLICATION)
    @DeleteMapping("/api/self-service/accounts/{accountId:" + ACCOUNT_ID + "}/email-change")
    ResponseEntity<Void> cancelEmailChange(@AuthenticationPrincipal ApplicationCaller caller,
            @PathVariable String accountId) {
        return cancelEmailChangeUseCase.execute(caller, accountId)
                ? ResponseEntity.noContent().header("Cache-Control", NO_STORE).build()
                : ResponseEntity.notFound().header("Cache-Control", NO_STORE).build();
    }

    record EmailChangeConfirmationRequest(@NotBlank String token) {
        @Override
        public String toString() {
            return "EmailChangeConfirmationRequest[token=[OMITIDO]]";
        }
    }

    record EmailChangeConfirmationResponse(String status) {
    }

    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/self-service/email-change/confirm")
    ResponseEntity<?> confirmEmailChange(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody EmailChangeConfirmationRequest request) {
        return switch (confirmEmailChangeUseCase.execute(caller, request.token())) {
            case AWAITING_OTHER_CONFIRMATION -> ResponseEntity.ok().header("Cache-Control", NO_STORE)
                    .body(new EmailChangeConfirmationResponse("awaiting-other-confirmation"));
            case COMPLETED -> ResponseEntity.ok().header("Cache-Control", NO_STORE)
                    .body(new EmailChangeConfirmationResponse("completed"));
            case INVALID_TOKEN -> error(HttpStatus.BAD_REQUEST, "invalid-email-change-token",
                    "Token de alteracao de e-mail invalido");
            case EMAIL_ALREADY_IN_USE -> error(HttpStatus.CONFLICT, "email-already-in-use",
                    "Endereco de e-mail ja esta em uso");
            case EMAIL_CHANGE_CONFLICT -> error(HttpStatus.CONFLICT, "email-change-conflict",
                    "Solicitacao de alteracao de e-mail incompativel");
        };
    }

    record PasswordChangeRequest(@NotBlank String currentPassword,
            @NotBlank @Size(max = 72) String newPassword) {
        @Override
        public String toString() {
            return "PasswordChangeRequest[currentPassword=[OMITIDA], newPassword=[OMITIDA]]";
        }
    }

    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/self-service/accounts/{accountId:" + ACCOUNT_ID + "}/password")
    ResponseEntity<?> changePassword(@AuthenticationPrincipal ApplicationCaller caller,
            @PathVariable String accountId, @Valid @RequestBody PasswordChangeRequest request) {
        ChangePasswordUseCase.Result result = changePasswordUseCase.execute(
                caller, accountId, request.currentPassword(), request.newPassword());
        return switch (result.outcome()) {
            case CHANGED -> ResponseEntity.noContent().header("Cache-Control", NO_STORE).build();
            case CURRENT_PASSWORD_INCORRECT -> error(HttpStatus.BAD_REQUEST,
                    "current-password-incorrect", "Senha atual incorreta");
            case PASSWORD_REJECTED -> error(HttpStatus.BAD_REQUEST,
                    "invalid-password", "A senha nao atende a politica atual");
            case NOT_FOUND -> ResponseEntity.notFound().header("Cache-Control", NO_STORE).build();
            case RATE_LIMITED -> rateLimited(result.retryAfterSeconds());
        };
    }

    @SecurityRequirement(name = APPLICATION)
    @GetMapping("/api/self-service/accounts/{accountId:" + ACCOUNT_ID + "}/session-state")
    ResponseEntity<GetSessionStateUseCase.SessionState> sessionState(
            @AuthenticationPrincipal ApplicationCaller caller,
            @PathVariable String accountId) {
        return getSessionStateUseCase.execute(caller, accountId)
                .map(state -> ResponseEntity.ok().header("Cache-Control", NO_STORE).body(state))
                .orElseGet(() -> ResponseEntity.notFound().header("Cache-Control", NO_STORE).build());
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).header("Cache-Control", NO_STORE)
                .body(problemDetailWriter.bodyOf(status, detail, code));
    }

    private ResponseEntity<?> rateLimited(long retryAfterSeconds) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Cache-Control", NO_STORE)
                .header("Retry-After", Long.toString(retryAfterSeconds))
                .body(problemDetailWriter.bodyOf(HttpStatus.TOO_MANY_REQUESTS,
                        "Limite de solicitações atingido", "rate-limit-exceeded"));
    }
}
