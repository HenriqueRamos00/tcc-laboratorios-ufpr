package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarTokenServicoRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.TokenServicoEmitidoResponse;
import br.ufpr.tcc.backend_auth.application.dto.response.TokenServicoResponse;
import br.ufpr.tcc.backend_auth.application.service.TokenServicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services/{serviceId}/tokens")
@RequiredArgsConstructor
@Tag(name = "Tokens de serviço")
@SecurityRequirement(name = "serviceAdmin")
public class TokenServicoController {
    private final TokenServicoService tokens;

    @PostMapping
    @Operation(summary = "Emitir token de serviço", description = "O segredo accessToken aparece somente nesta resposta. Permite consultar e autenticar exclusivamente os clientes deste serviço; não permite alterações ou administração.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Token emitido; copie accessToken agora"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ServiceNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/ServiceConflict")})
    public ResponseEntity<TokenServicoEmitidoResponse> emitir(@PathVariable Long serviceId,
            @Valid @RequestBody CriarTokenServicoRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(tokens.emitir(serviceId, request));
    }

    @GetMapping
    @Operation(summary = "Listar tokens de serviço", description = "Somente metadados, incluindo tokens expirados. Não retorna segredos ou hashes; tokens revogados são removidos.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Metadados dos tokens"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ServiceNotFound")})
    public List<TokenServicoResponse> listar(@PathVariable Long serviceId) {
        return tokens.listar(serviceId);
    }

    @DeleteMapping("/{tokenId}")
    @Operation(summary = "Revogar token de serviço", description = "Invalida o token para as próximas requisições. Não altera outros tokens do mesmo serviço.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Token revogado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ServiceNotFound")})
    public ResponseEntity<Void> revogar(@PathVariable Long serviceId, @PathVariable Long tokenId) {
        tokens.revogar(serviceId, tokenId);
        return ResponseEntity.noContent().build();
    }
}
