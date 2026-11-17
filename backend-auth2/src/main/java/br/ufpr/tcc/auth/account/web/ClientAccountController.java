package br.ufpr.tcc.auth.account.web;

import br.ufpr.tcc.auth.account.ClientAccountService;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import br.ufpr.tcc.auth.shared.security.CallerScope;
import br.ufpr.tcc.auth.shared.web.ProblemDetailWriter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 * Rotas de contas, confirmacao e recuperacao. A SecurityConfiguration autoriza escrita
 * administrativa, leitura por ambos os tipos de credencial e operacoes de token pela
 * aplicacao.
 */
@RestController
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ClientAccountController {

    private static final String ADMINISTRATIVE = "credencial-administrativa";
    private static final String APPLICATION = "credencial-de-aplicacao";

    /** Identificador como texto de digitos, que e como o servico os publica. */
    private static final String IDENTIFIER = "[0-9]{1,19}";

    private final ClientAccountService service;
    private final ProblemDetailWriter problemDetailWriter;

    /**
     * Pedido de cadastro: aplicacao obrigatoria e empresas opcionais. Lista ausente significa
     * nenhum vinculo.
     */
    record CreateClientRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72)
            @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                    minLength = 8,
                    description = "Senha, preservando espacos: o servidor nao corta espaco nas pontas,\n"
                            + " nao muda a caixa e nao remove acentuacao, porque cada uma dessas trocas\n"
                            + " mudaria a credencial em silencio. O minimo de oito, mais maiuscula,\n"
                            + " minuscula e digito, e cobrado pelo caso de uso e nao por anotacao.")
            String password,
            @Pattern(regexp = "CLIENTE", message = "Perfil deve ser CLIENTE")
            @Schema(pattern = "^CLIENTE$", description = "Unico perfil aceito hoje.")
            String role,
            @NotBlank @Pattern(regexp = IDENTIFIER, message = "Servico deve ser um identificador")
            @Schema(description = "Identificador do servico ao qual o cliente pertence.")
            String serviceId,
            @Size(max = 100)
            @Schema(description = "Identificadores das empresas que o cliente representa."
                    + " Omitido, null ou [] significa nenhuma empresa.")
            List<@NotNull @Pattern(regexp = IDENTIFIER,
                    message = "Empresa deve ser um identificador") String> companyIds) {

        @Override
        public String toString() {
            return "CreateClientRequest[email=" + email + ", password=[OMITIDA]]";
        }
    }

    /**
     * Converte o resultado do cadastro em HTTP e identifica a referencia invalida no corpo da
     * recusa.
     */
    @SecurityRequirement(name = ADMINISTRATIVE)
    @PostMapping("/api/users")
    ResponseEntity<ClientAccountService.AccountSummary> create(@Valid @RequestBody CreateClientRequest request) {
        Optional<Long> applicationId = identifierOf(request.serviceId());
        if (applicationId.isEmpty()) {
            return refusal(HttpStatus.NOT_FOUND, "Servico nao encontrado");
        }
        Optional<List<Long>> companyIds = identifiersOf(request.companyIds());
        if (companyIds.isEmpty()) {
            return refusal(HttpStatus.NOT_FOUND, "Empresa nao encontrada");
        }
        ClientAccountService.Registration registration = service.register(applicationId.get(),
                request.name(), request.email(), request.password(), companyIds.get());
        return switch (registration.outcome()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(registration.account());
            case EMAIL_TAKEN -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case PASSWORD_REJECTED -> ResponseEntity.badRequest().build();
            case SERVICE_NOT_FOUND -> refusal(HttpStatus.NOT_FOUND, "Servico nao encontrado");
            case SERVICE_INACTIVE -> refusal(HttpStatus.CONFLICT, "Servico inativo");
            case COMPANY_NOT_FOUND -> refusal(HttpStatus.NOT_FOUND, "Empresa nao encontrada");
        };
    }

    record ConfirmationRequest(@NotBlank String token) {

        @Override
        public String toString() {
            return "ConfirmationRequest[token=[OMITIDO]]";
        }
    }

    /** Lista contas no escopo do chamador: aplicacao ve apenas suas contas; administrador ve todas. */
    @SecurityRequirement(name = ADMINISTRATIVE)
    @SecurityRequirement(name = APPLICATION)
    @GetMapping("/api/users")
    ResponseEntity<List<ClientAccountService.AccountSummary>> list(
            @AuthenticationPrincipal Object caller) {
        return ResponseEntity.ok(service.list(CallerScope.applicationScopeOf(caller)));
    }

    @SecurityRequirement(name = ADMINISTRATIVE)
    @SecurityRequirement(name = APPLICATION)
    @GetMapping("/api/users/{id}")
    ResponseEntity<ClientAccountService.AccountSummary> read(@AuthenticationPrincipal Object caller,
            @PathVariable String id) {
        return service.find(CallerScope.applicationScopeOf(caller), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Altera somente nome, situacao obrigatoria e empresas. A lista substitui todos os vinculos;
     * ausente ou vazia remove todos. E-mail, perfil e aplicacao nao sao alteraveis por esta rota.
     */
    record UpdateClientRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull Boolean active,
            @Size(max = 100)
            @Schema(description = "Identificadores das empresas que o cliente representa."
                    + " Substitui a lista inteira; omitido, null ou [] significa nenhuma"
                    + " empresa.")
            List<@NotNull @Pattern(regexp = IDENTIFIER,
                    message = "Empresa deve ser um identificador") String> companyIds) {
    }

    @SecurityRequirement(name = ADMINISTRATIVE)
    @PutMapping("/api/users/{id}")
    ResponseEntity<ClientAccountService.AccountSummary> update(@AuthenticationPrincipal Object caller,
            @PathVariable String id, @Valid @RequestBody UpdateClientRequest request) {
        Optional<List<Long>> companyIds = identifiersOf(request.companyIds());
        if (companyIds.isEmpty()) {
            return refusal(HttpStatus.NOT_FOUND, "Empresa nao encontrada");
        }
        ClientAccountService.Change change = service.update(CallerScope.applicationScopeOf(caller),
                id, request.name(), request.active(), companyIds.get());
        return switch (change.outcome()) {
            case CHANGED -> ResponseEntity.ok(change.account());
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case COMPANY_NOT_FOUND -> refusal(HttpStatus.NOT_FOUND, "Empresa nao encontrada");
        };
    }

    @SecurityRequirement(name = ADMINISTRATIVE)
    @DeleteMapping("/api/users/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Object caller, @PathVariable String id) {
        return service.delete(CallerScope.applicationScopeOf(caller), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** Monta recusa com detalhe preservando o tipo declarado para a documentacao da API. */
    private <T> ResponseEntity<T> refusal(HttpStatus status, String detail) {
        return ResponseEntity.of(problemDetailWriter.bodyOf(status, detail)).build();
    }

    /** Identificador fora do intervalo de long e tratado como referencia inexistente. */
    private static Optional<Long> identifierOf(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
        }
    }

    /** Lista ausente vale lista vazia; vazio no retorno e algum identificador que estourou. */
    private static Optional<List<Long>> identifiersOf(List<String> values) {
        if (values == null) {
            return Optional.of(List.of());
        }
        List<Long> identifiers = new ArrayList<>();
        for (String value : values) {
            Optional<Long> identifier = identifierOf(value);
            if (identifier.isEmpty()) {
                return Optional.empty();
            }
            identifiers.add(identifier.get());
        }
        return Optional.of(identifiers);
    }

    record RecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    /** Responde sempre 202, exista ou nao o endereco. */
    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/auth/recoveries")
    ResponseEntity<Void> requestRecovery(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody RecoveryRequest request) {
        service.requestPasswordRecovery(caller, request.email());
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
        return service.resetPassword(caller, request.token(), request.password())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }

    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/auth/confirmations")
    ResponseEntity<Void> confirm(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody ConfirmationRequest request) {
        return service.confirmEmail(caller, request.token())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }
}
