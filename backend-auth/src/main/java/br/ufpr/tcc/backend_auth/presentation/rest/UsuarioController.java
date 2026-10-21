package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarUsuarioRequest;
import br.ufpr.tcc.backend_auth.application.dto.request.AtualizarUsuarioRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.UsuarioResponse;
import br.ufpr.tcc.backend_auth.application.dto.response.UsuarioDetalheResponse;
import br.ufpr.tcc.backend_auth.application.usecase.CriarUsuarioUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ConsultarUsuariosUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.AtualizarUsuarioUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ExcluirUsuarioUseCase;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Clientes dos serviços")
public class UsuarioController {
    private final CriarUsuarioUseCase criarUsuario;
    private final ConsultarUsuariosUseCase consultar;
    private final AtualizarUsuarioUseCase atualizar;
    private final ExcluirUsuarioUseCase excluir;

    @PostMapping
    @Operation(summary = "Cadastrar cliente", description = "Cria um cliente ativo associado ao serviceId informado, sem administração do Backend Auth.")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuário cadastrado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ServiceNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/ClientConflict")
    })
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody CriarUsuarioRequest request) {
        return ResponseEntity.status(201).body(criarUsuario.execute(request));
    }

    @GetMapping
    @Operation(summary = "Listar clientes", description = "Inclui contas ativas e inativas. Token de serviço vê somente seus clientes; administrador vê todos.")
    @SecurityRequirement(name = "serviceAdmin")
    @SecurityRequirement(name = "serviceToken")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Usuários"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials")})
    public List<UsuarioDetalheResponse> listar(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        return consultar.listar(principal.getAttribute("serviceId"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar cliente", description = "Com token de serviço, IDs de outros serviços e clientes sem associação retornam 404.")
    @SecurityRequirement(name = "serviceAdmin")
    @SecurityRequirement(name = "serviceToken")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Usuário"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound")})
    public UsuarioDetalheResponse obter(@PathVariable Long id,
            @AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        return consultar.obter(id, principal.getAttribute("serviceId"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cliente", description = "Informe serviceId para associar ou transferir o cliente. Novas associações exigem serviço ativo; editar um cliente já associado a serviço inativo é permitido. Omita password para preservar a senha.")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Usuário atualizado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/ClientConflict")})
    public UsuarioDetalheResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuarioRequest request) {
        return atualizar.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cliente", description = "Remove a conta, preservando eventual permissão de administração do Backend Auth.")
    @SecurityRequirement(name = "serviceAdmin")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Usuário excluído"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/AccountNotFound")})
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        excluir.execute(id);
        return ResponseEntity.noContent().build();
    }
}
