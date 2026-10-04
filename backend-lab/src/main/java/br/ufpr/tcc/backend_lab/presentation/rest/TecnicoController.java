package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.ChangeTecnicoStatusRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.CreateTecnicoRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.UpdateTecnicoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoStatus;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeTecnicoStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateTecnicoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListTecnicosUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateTecnicoUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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

@RestController
@RequestMapping("/api/technicians")
@RequiredArgsConstructor
public class TecnicoController {
	private final ListTecnicosUseCase listTecnicosUseCase;
	private final CreateTecnicoUseCase createTecnicoUseCase;
	private final UpdateTecnicoUseCase updateTecnicoUseCase;
	private final ChangeTecnicoStatusUseCase changeTecnicoStatusUseCase;

	@GetMapping
	public ResponseEntity<List<TecnicoResponse>> pesquisar(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) TecnicoStatus status
	) {
		return ResponseEntity.ok(listTecnicosUseCase.execute(search, status));
	}

	@PostMapping
	public ResponseEntity<TecnicoResponse> cadastrar(@Valid @RequestBody CreateTecnicoRequest request) {
		TecnicoResponse tecnico = createTecnicoUseCase.execute(request);
		return ResponseEntity.created(URI.create("/api/technicians/" + tecnico.id())).body(tecnico);
	}

	@PutMapping("/{id}")
	public ResponseEntity<TecnicoResponse> editar(
			@PathVariable Long id,
			@Valid @RequestBody UpdateTecnicoRequest request
	) {
		return ResponseEntity.ok(updateTecnicoUseCase.execute(id, request));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<TecnicoResponse> alterarStatus(
			@PathVariable Long id,
			@Valid @RequestBody ChangeTecnicoStatusRequest request
	) {
		return ResponseEntity.ok(changeTecnicoStatusUseCase.execute(id, request.status()));
	}
}
