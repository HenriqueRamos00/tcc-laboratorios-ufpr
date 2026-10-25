package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.AtualizarServicoRequest;
import br.ufpr.tcc.backend_auth.application.dto.request.CriarServicoRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.ServicoResponse;
import br.ufpr.tcc.backend_auth.application.usecase.AtualizarServicoUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ConsultarServicosUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.CriarServicoUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ExcluirServicoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Serviços")
@SecurityRequirement(name = "serviceAdmin")
public class ServicoController {
    private final CriarServicoUseCase criar;
    private final ConsultarServicosUseCase consultar;
    private final AtualizarServicoUseCase atualizar;
    private final ExcluirServicoUseCase excluir;

    @PostMapping
    @Operation(summary = "Cadastrar serviço", description = "Somente administradores do Backend Auth. Cria um serviço ativo, por exemplo portal-web.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Serviço cadastrado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/ServiceConflict")})
    public ResponseEntity<ServicoResponse> criar(@Valid @RequestBody CriarServicoRequest request) {
        return ResponseEntity.status(201).body(criar.execute(request));
    }

    @GetMapping
    @Operation(summary = "Listar serviços", description = "Inclui serviços ativos e inativos, ordenados por nome e ID.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Serviços"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")})
    public List<ServicoResponse> listar() {
        return consultar.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar serviço")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Serviço"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ServiceNotFound")})
    public ServicoResponse obter(@PathVariable Long id) {
        return consultar.obter(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar serviço", description = "Desativar revoga todos os seus tokens; reativar exige emitir novos tokens. Não altera as contas associadas.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Serviço atualizado"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ServiceNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/ServiceConflict")})
    public ServicoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarServicoRequest request) {
        return atualizar.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir serviço", description = "Exige reassociar ou excluir seus clientes antes. Revoga todos os tokens do serviço excluído.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Serviço excluído"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ServiceNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/ServiceConflict")})
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        excluir.execute(id);
        return ResponseEntity.noContent().build();
    }
}
