package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarAdministradorRequest;
import br.ufpr.tcc.backend_auth.application.dto.request.AtualizarAdministradorRequest;
import br.ufpr.tcc.backend_auth.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorResponse;
import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorDetalheResponse;
import br.ufpr.tcc.backend_auth.application.dto.response.LoginAdministradorResponse;
import br.ufpr.tcc.backend_auth.application.usecase.CriarAdministradorUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.LoginAdministradorUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ConsultarAdministradoresUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.AtualizarAdministradorUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ExcluirAdministradorUseCase;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Administração do serviço")
public class AdministradorController {
    private final CriarAdministradorUseCase criarAdministrador;
    private final LoginAdministradorUseCase login;
    private final ConsultarAdministradoresUseCase consultar;
    private final AtualizarAdministradorUseCase atualizar;
    private final ExcluirAdministradorUseCase excluir;

    @PostMapping("/api/admins")
    @Operation(summary = "Cadastrar administrador do serviço", description = "Não atribui um perfil de portal à conta criada.")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Administrador cadastrado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/EmailConflict")
    })
    public ResponseEntity<AdministradorResponse> criar(@Valid @RequestBody CriarAdministradorRequest request) {
        return ResponseEntity.status(201).body(criarAdministrador.execute(request));
    }

    @PostMapping("/api/admin/auth/login")
    @Operation(summary = "Login administrativo", description = "Emite um Bearer token opaco e temporário, exclusivo para administrar este serviço. Cole accessToken em Authorize → serviceAdmin.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Administrador autenticado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials")
    })
    public ResponseEntity<LoginAdministradorResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(login.execute(request));
    }

    @GetMapping("/api/admins")
    @Operation(summary = "Listar administradores", description = "Inclui contas ativas e inativas, ordenadas por nome e ID.")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Administradores"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials")})
    public List<AdministradorDetalheResponse> listar() {
        return consultar.listar();
    }

    @GetMapping("/api/admins/{id}")
    @Operation(summary = "Consultar administrador")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Administrador"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound")})
    public AdministradorDetalheResponse obter(@PathVariable Long id) {
        return consultar.obter(id);
    }

    @PutMapping("/api/admins/{id}")
    @Operation(summary = "Atualizar administrador", description = "Omita password para preservar a senha. Alterar a senha ou desativar a conta revoga seus tokens. Não permite desativar o último administrador ativo.")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Administrador atualizado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/AccountConflict")})
    public AdministradorDetalheResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarAdministradorRequest request) {
        return atualizar.execute(id, request);
    }

    @DeleteMapping("/api/admins/{id}")
    @Operation(summary = "Excluir administrador", description = "Revoga tokens e remove a administração. Preserva eventual perfil de portal. Não permite remover o último administrador ativo.")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Administrador excluído"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/AccountConflict")})
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        excluir.execute(id);
        return ResponseEntity.noContent().build();
    }
}
