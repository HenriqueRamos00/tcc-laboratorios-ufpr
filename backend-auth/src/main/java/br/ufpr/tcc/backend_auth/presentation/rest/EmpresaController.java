package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.EmpresaRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_auth.application.usecase.AtualizarEmpresaUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ConsultarEmpresasUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.CriarEmpresaUseCase;
import br.ufpr.tcc.backend_auth.application.usecase.ExcluirEmpresaUseCase;
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
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Empresas")
@SecurityRequirement(name = "serviceAdmin")
public class EmpresaController {
    private final CriarEmpresaUseCase criar;
    private final ConsultarEmpresasUseCase consultar;
    private final AtualizarEmpresaUseCase atualizar;
    private final ExcluirEmpresaUseCase excluir;

    @PostMapping
    @Operation(summary = "Cadastrar empresa", description = "CNPJ único entre todas as empresas. Aceita formato numérico ou alfanumérico, com ou sem máscara.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Empresa cadastrada"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/CompanyConflict")})
    public ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.status(201).body(criar.execute(request));
    }

    @GetMapping
    @Operation(summary = "Listar empresas", description = "Somente administradores. Integrações recebem apenas os CNPJs associados aos clientes do seu serviço.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Empresas ordenadas por nome e ID"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")})
    public List<EmpresaResponse> listar() {
        return consultar.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar empresa")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Empresa"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/CompanyNotFound")})
    public EmpresaResponse obter(@PathVariable Long id) {
        return consultar.obter(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar empresa", description = "Recebe name e cnpj. Alterar CNPJ atualiza as autorizações dos clientes associados nas próximas consultas e logins.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Empresa atualizada"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/CompanyNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/CompanyConflict")})
    public EmpresaResponse atualizar(@PathVariable Long id, @Valid @RequestBody EmpresaRequest request) {
        return atualizar.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir empresa", description = "Remova primeiro as associações da empresa com os clientes. As outras empresas dos clientes são preservadas.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Empresa excluída"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidCredentials"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/CompanyNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/CompanyConflict")})
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        excluir.execute(id);
        return ResponseEntity.noContent().build();
    }
}
