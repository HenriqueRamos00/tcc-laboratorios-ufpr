package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EquipamentoResponse;
import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEquipamentosUseCase {
	private final EquipamentoRepository equipamentoRepository;

	@Transactional(readOnly = true)
	public List<EquipamentoResponse> execute(String search, TipoEquipamento type, NivelRisco risk) {
		String busca = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		return equipamentoRepository.pesquisarEquipamentos(busca, type, risk)
				.stream().map(EquipamentoResponse::fromEntity).toList();
	}
}
