package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.EquipamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EquipamentoResponse;
import br.ufpr.tcc.backend_lab.application.usecase.CreateEquipamentoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.DeactivateEquipamentoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListEquipamentosUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateEquipamentoUseCase;
import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipments")
@RequiredArgsConstructor
public class EquipamentoController {
	private final ListEquipamentosUseCase listEquipamentosUseCase;
	private final CreateEquipamentoUseCase createEquipamentoUseCase;
	private final UpdateEquipamentoUseCase updateEquipamentoUseCase;
	private final DeactivateEquipamentoUseCase deactivateEquipamentoUseCase;

	@GetMapping
	public ResponseEntity<List<EquipamentoResponse>> pesquisar(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) TipoEquipamento type,
			@RequestParam(required = false) NivelRisco risk
	) {
		return ResponseEntity.ok(listEquipamentosUseCase.execute(search, type, risk));
	}

	@PostMapping
	public ResponseEntity<EquipamentoResponse> cadastrar(@Valid @RequestBody EquipamentoRequest request) {
		EquipamentoResponse equipamento = createEquipamentoUseCase.execute(request);
		return ResponseEntity.created(URI.create("/api/equipments/" + equipamento.id())).body(equipamento);
	}

	@PutMapping("/{id}")
	public ResponseEntity<EquipamentoResponse> editar(@PathVariable Long id, @Valid @RequestBody EquipamentoRequest request) {
		return ResponseEntity.ok(updateEquipamentoUseCase.execute(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> desativar(@PathVariable Long id) {
		deactivateEquipamentoUseCase.execute(id);
		return ResponseEntity.noContent().build();
	}
}
