package br.ufpr.tcc.auth.account.web;

import br.ufpr.tcc.auth.account.AccountLifecycle;
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
 * Cadastro de cliente, confirmacao do endereco de e-mail e recuperacao de senha.
 *
 * As rotas tem dois publicos, e a cadeia de seguranca e quem os separa. Cadastrar, alterar
 * e excluir conta e do administrador, pelo portal administrativo. Ler conta e das duas
 * credenciais: a aplicacao enxerga as contas dela, e o administrador atravessa aplicacoes.
 * Confirmacao e recuperacao sao da aplicacao, porque e ela que recebe o link das maos do
 * cliente.
 */
@RestController
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ClientAccountController {

    private static final String ADMINISTRATIVE = "credencial-administrativa";
    private static final String APPLICATION = "credencial-de-aplicacao";

    /** Identificador como texto de digitos, que e como o servico os publica. */
    private static final String IDENTIFIER = "[0-9]{1,19}";

    private final AccountLifecycle lifecycle;
    private final ProblemDetailWriter problemDetailWriter;

    /**
     * A aplicacao e obrigatoria e vem no corpo: quem cadastra e o administrador, que
     * atravessa aplicacoes, entao nao existe credencial de onde tira-la.
     *
     * A lista de empresas e opcional, e ausente vale lista vazia: a conta nasce sem
     * representar empresa nenhuma, e o administrador vincula depois pela alteracao.
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
     * O corpo da recusa diz qual referencia falhou, porque o pedido carrega duas, a
     * aplicacao e as empresas, e o 404 sozinho nao deixaria o portal dizer ao administrador
     * qual delas corrigir.
     */
    @SecurityRequirement(name = ADMINISTRATIVE)
    @PostMapping("/api/users")
    ResponseEntity<AccountLifecycle.AccountSummary> create(@Valid @RequestBody CreateClientRequest request) {
        Optional<Long> applicationId = identifierOf(request.serviceId());
        if (applicationId.isEmpty()) {
            return refusal(HttpStatus.NOT_FOUND, "Servico nao encontrado");
        }
        Optional<List<Long>> companyIds = identifiersOf(request.companyIds());
        if (companyIds.isEmpty()) {
            return refusal(HttpStatus.NOT_FOUND, "Empresa nao encontrada");
        }
        AccountLifecycle.Registration registration = lifecycle.register(applicationId.get(),
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

    /**
     * Listagem de contas no alcance de quem chamou.
     *
     * Aplicacao enxerga as contas dela; administrador atravessa aplicacoes. O filtro vai
     * na consulta e nunca na memoria depois: consultar tudo e descartar devolveria o mesmo
     * corpo e traria dado alheio para dentro do processo.
     */
    @SecurityRequirement(name = ADMINISTRATIVE)
    @SecurityRequirement(name = APPLICATION)
    @GetMapping("/api/users")
    ResponseEntity<List<AccountLifecycle.AccountSummary>> list(
            @AuthenticationPrincipal Object caller) {
        return ResponseEntity.ok(lifecycle.list(CallerScope.applicationScopeOf(caller)));
    }

    @SecurityRequirement(name = ADMINISTRATIVE)
    @SecurityRequirement(name = APPLICATION)
    @GetMapping("/api/users/{id}")
    ResponseEntity<AccountLifecycle.AccountSummary> read(@AuthenticationPrincipal Object caller,
            @PathVariable String id) {
        return lifecycle.find(CallerScope.applicationScopeOf(caller), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * A alteracao muda nome, situacao e empresas, e declara apenas isso.
     *
     * Endereco, perfil e aplicacao nao estao aqui porque a rota nao os honra, e campo
     * declarado que a rota descarta e pior que campo ausente: quem manda endereco novo
     * receberia 200 com o endereco antigo. Trocar o endereco e operacao propria, com
     * confirmacao do novo endereco, e ela ainda nao existe; perfil tem um unico valor; e
     * conta nao muda de aplicacao. Campo fora desta lista responde 400, porque a
     * desserializacao recusa o que nao conhece.
     *
     * A situacao e obrigatoria porque ela decide se a conta entra, entao nao muda por
     * omissao. Enquanto era opcional e ausente valia verdadeiro, um pedido de renomeacao
     * reativava conta desativada em silencio.
     *
     * A lista de empresas substitui a anterior inteira, e ausente vale lista vazia. Isso e
     * o contrato que o portal administrativo ja conhece: a alteracao declara o estado que a
     * conta passa a ter, e nao uma diferenca sobre o estado anterior.
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
    ResponseEntity<AccountLifecycle.AccountSummary> update(@AuthenticationPrincipal Object caller,
            @PathVariable String id, @Valid @RequestBody UpdateClientRequest request) {
        Optional<List<Long>> companyIds = identifiersOf(request.companyIds());
        if (companyIds.isEmpty()) {
            return refusal(HttpStatus.NOT_FOUND, "Empresa nao encontrada");
        }
        AccountLifecycle.Change change = lifecycle.update(CallerScope.applicationScopeOf(caller),
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
        return lifecycle.delete(CallerScope.applicationScopeOf(caller), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Recusa com corpo, sem abrir mao do tipo de retorno da rota: e o tipo declarado que o
     * gerador do contrato publica, e devolver objeto generico apagaria do artefato a forma
     * da conta.
     */
    private <T> ResponseEntity<T> refusal(HttpStatus status, String detail) {
        return ResponseEntity.of(problemDetailWriter.bodyOf(status, detail)).build();
    }

    /**
     * Identificador que passa do tamanho de um numero do servico e identificador que nao
     * existe: o formato ja foi conferido na validacao, e o que sobra aqui e o estouro.
     */
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
        lifecycle.requestPasswordRecovery(caller, request.email());
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
        return lifecycle.resetPassword(caller, request.token(), request.password())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }

    @SecurityRequirement(name = APPLICATION)
    @PostMapping("/api/auth/confirmations")
    ResponseEntity<Void> confirm(@AuthenticationPrincipal ApplicationCaller caller,
            @Valid @RequestBody ConfirmationRequest request) {
        return lifecycle.confirmEmail(caller, request.token())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().build();
    }
}
