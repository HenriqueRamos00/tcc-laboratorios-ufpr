package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.ChangeCadastroStatusRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.CreateEmpresaRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.UpdateEmpresaRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeEmpresaStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateEmpresaUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListEmpresasUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateEmpresaUseCase;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class EmpresaController {
    private final ListEmpresasUseCase listar;
    private final CreateEmpresaUseCase criar;
    private final UpdateEmpresaUseCase editar;
    private final ChangeEmpresaStatusUseCase status;

    @GetMapping
    public List<EmpresaResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CadastroStatus status) {
        return listar.execute(search, status);
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody CreateEmpresaRequest request) {
        var response = criar.execute(request);
        return ResponseEntity.created(URI.create("/api/companies/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public EmpresaResponse editar(
            @PathVariable Long id, @Valid @RequestBody UpdateEmpresaRequest request) {
        return editar.execute(id, request);
    }

    @PatchMapping("/{id}/status")
    public EmpresaResponse status(
            @PathVariable Long id, @Valid @RequestBody ChangeCadastroStatusRequest request) {
        return status.execute(id, request.status());
    }
}
