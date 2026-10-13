package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.ChangeCadastroStatusRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.CreateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.UpdateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResponse;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeClienteStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListClientesUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateClienteUseCase;

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
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClienteController {
    private final ListClientesUseCase listar;
    private final CreateClienteUseCase criar;
    private final UpdateClienteUseCase editar;
    private final ChangeClienteStatusUseCase status;

    @GetMapping
    public List<ClienteResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CadastroStatus status) {
        return listar.execute(search, status);
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody CreateClienteRequest request) {
        var response = criar.execute(request);
        return ResponseEntity.created(URI.create("/api/clients/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public ClienteResponse editar(
            @PathVariable Long id, @Valid @RequestBody UpdateClienteRequest request) {
        return editar.execute(id, request);
    }

    @PatchMapping("/{id}/status")
    public ClienteResponse status(
            @PathVariable Long id, @Valid @RequestBody ChangeCadastroStatusRequest request) {
        return status.execute(id, request.status());
    }
}
