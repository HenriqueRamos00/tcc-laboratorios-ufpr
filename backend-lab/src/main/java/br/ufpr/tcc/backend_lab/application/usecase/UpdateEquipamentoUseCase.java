package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.EquipamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EquipamentoResponse;
import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateEquipamentoUseCase {
	private final EquipamentoRepository equipamentoRepository;

	@Transactional
	public EquipamentoResponse execute(Long id, EquipamentoRequest request) {
		Equipamento equipamento = equipamentoRepository.findById(id)
				.filter(Equipamento::isAtivo).orElseThrow(() -> new EquipamentoNaoEncontradoException(id));
		equipamento.setNumeroSerie(request.serialNumber());
		equipamento.setTag(request.tag());
		equipamento.setTipo(request.type());
		equipamento.setSubestacao(request.substation());
		equipamento.setTensaoPrimaria(new BigDecimal(request.primaryVoltage()));
		equipamento.setProprietario(request.owner());
		equipamento.setNivelRisco(request.riskLevel());
		return EquipamentoResponse.fromEntity(equipamentoRepository.saveAndFlush(equipamento));
	}
}
