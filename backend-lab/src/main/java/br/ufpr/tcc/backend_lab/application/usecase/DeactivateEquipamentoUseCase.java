package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeactivateEquipamentoUseCase {
	private final EquipamentoRepository equipamentoRepository;

	@Transactional
	public void execute(Long id) {
		Equipamento equipamento = equipamentoRepository.findById(id)
				.orElseThrow(() -> new EquipamentoNaoEncontradoException(id));
		equipamento.setAtivo(false);
	}
}
