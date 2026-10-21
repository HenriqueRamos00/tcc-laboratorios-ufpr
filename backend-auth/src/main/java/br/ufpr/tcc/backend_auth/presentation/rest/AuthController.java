package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.LoginUsuarioResponse;
import br.ufpr.tcc.backend_auth.application.usecase.LoginUseCase;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Login de clientes")
public class AuthController {
    private final LoginUseCase login;

    @PostMapping("/login")
    @Operation(summary = "Autenticar cliente do serviço", description = "Exige token do serviço do cliente. Retorna identidade e serviceCode, sem emitir token ou sessão.")
    @SecurityRequirement(name = "serviceToken")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cliente autenticado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    public ResponseEntity<LoginUsuarioResponse> login(@Valid @RequestBody LoginRequest request,
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(login.execute(request, principal.getAttribute("serviceId")));
    }
}
