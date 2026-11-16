package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.EquipamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EquipamentoResponse;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorEmpresaEquipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateEquipamentoUseCase {
	private final EquipamentoRepository equipamentoRepository;
	private final ResolvedorEmpresaEquipamento empresas;

	@Transactional
	public EquipamentoResponse execute(EquipamentoRequest request) {
		Equipamento equipamento = new Equipamento();
		equipamento.setNumeroSerie(request.serialNumber());
		equipamento.setTag(request.tag());
		equipamento.setTipo(request.type());
		equipamento.setSubestacao(request.substation());
		equipamento.setTensaoPrimaria(new BigDecimal(request.primaryVoltage()));
		equipamento.setFabricante(request.manufacturer());
		equipamento.setAnoFabricacao(request.manufactureYear());
		equipamento.setPotenciaMva(decimalOpcional(request.powerMva()));
		equipamento.setTipoOleo(request.oilType());
		equipamento.setVolumeOleoLitros(decimalOpcional(request.oilVolumeLiters()));
		equipamento.setEmpresa(empresas.resolver(request.companyId(), null));
		equipamento.setNivelRisco(request.riskLevel());
		return EquipamentoResponse.fromEntity(equipamentoRepository.saveAndFlush(equipamento));
	}

	private static BigDecimal decimalOpcional(String value) {
		return value == null ? null : new BigDecimal(value);
	}
}
