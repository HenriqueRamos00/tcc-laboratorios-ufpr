package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.AlterarMinhaSenhaRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.PatchMeuPerfilRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.MeuPerfilResponse;
import br.ufpr.tcc.backend_lab.application.usecase.EmailChangeService;
import br.ufpr.tcc.backend_lab.application.usecase.MeuPerfilUseCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeuPerfilController {
    private final MeuPerfilUseCase meuPerfil;
    private final EmailChangeService emailChange;

    @GetMapping("/api/auth/internal/profile")
    public ResponseEntity<MeuPerfilResponse> consultarInterno(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(meuPerfil.consultar(idAutenticado(authentication)));
    }

    @PatchMapping("/api/auth/internal/profile")
    public ResponseEntity<MeuPerfilResponse> atualizarInterno(
            Authentication authentication, @Valid @RequestBody PatchMeuPerfilRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(meuPerfil.atualizar(idAutenticado(authentication), request));
    }

    @PostMapping("/api/auth/internal/profile/password")
    public ResponseEntity<Void> alterarSenhaInterna(
            Authentication authentication, @Valid @RequestBody AlterarMinhaSenhaRequest request) {
        meuPerfil.alterarSenha(idAutenticado(authentication), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/api/client/profile")
    public ResponseEntity<MeuPerfilResponse> consultarCliente(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(meuPerfil.consultar(idAutenticado(authentication)));
    }

    @PatchMapping("/api/client/profile")
    public ResponseEntity<MeuPerfilResponse> atualizarCliente(
            Authentication authentication, @Valid @RequestBody PatchMeuPerfilRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(meuPerfil.atualizar(idAutenticado(authentication), request));
    }

    @PostMapping("/api/client/profile/password")
    public ResponseEntity<Void> alterarSenhaCliente(
            Authentication authentication, @Valid @RequestBody AlterarMinhaSenhaRequest request) {
        meuPerfil.alterarSenha(idAutenticado(authentication), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping({"/api/auth/internal/profile/email-change", "/api/client/profile/email-change"})
    public ResponseEntity<MeuPerfilResponse.PendingEmailChangeResponse> solicitarAlteracaoEmail(
            Authentication authentication, @Valid @RequestBody SolicitarAlteracaoEmailRequest request) {
        var pendente = emailChange.solicitar(
                idAutenticado(authentication), request.newEmail(), request.currentPassword());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(pendente);
    }

    @DeleteMapping({"/api/auth/internal/profile/email-change", "/api/client/profile/email-change"})
    public ResponseEntity<Void> cancelarAlteracaoEmail(Authentication authentication) {
        emailChange.cancelar(idAutenticado(authentication));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping({"/api/auth/internal/profile/email-change/confirm", "/api/client/profile/email-change/confirm"})
    public ResponseEntity<ConfirmacaoAlteracaoEmailResponse> confirmarAlteracaoEmail(
            @Valid @RequestBody ConfirmacaoAlteracaoEmailRequest request) {
        String status = emailChange.confirmar(request.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ConfirmacaoAlteracaoEmailResponse(status));
    }

    private Long idAutenticado(Authentication authentication) {
        Object principalId = Objects.requireNonNull(authentication).getDetails();
        if (principalId instanceof Long id && id > 0) {
            return id;
        }
        throw new IllegalStateException("Identidade autenticada inválida");
    }

    public record SolicitarAlteracaoEmailRequest(
            @NotBlank @Email @Size(max = 254) String newEmail,
            @NotBlank String currentPassword) {}

    public record ConfirmacaoAlteracaoEmailRequest(@NotBlank @Size(max = 128) String token) {}

    public record ConfirmacaoAlteracaoEmailResponse(String status) {}
}
