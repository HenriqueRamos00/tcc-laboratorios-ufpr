package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import java.math.BigDecimal;

public record EquipamentoResponse(
		Long id,
		String serialNumber,
		String tag,
		TipoEquipamento type,
		String substation,
		String primaryVoltage,
		String manufacturer,
		Integer manufactureYear,
		String powerMva,
		String oilType,
		String oilVolumeLiters,
		String companyId,
		String owner,
		NivelRisco riskLevel
) {
	public static EquipamentoResponse fromEntity(Equipamento equipamento) {
		return new EquipamentoResponse(
				equipamento.getId(), equipamento.getNumeroSerie(), equipamento.getTag(),
				equipamento.getTipo(), equipamento.getSubestacao(),
				equipamento.getTensaoPrimaria().stripTrailingZeros().toPlainString(),
				equipamento.getFabricante(), equipamento.getAnoFabricacao(),
				decimal(equipamento.getPotenciaMva()), equipamento.getTipoOleo(),
				decimal(equipamento.getVolumeOleoLitros()),
				equipamento.getEmpresa().getId().toString(),
				equipamento.getEmpresa().getLegalName(), equipamento.getNivelRisco()
		);
	}

	private static String decimal(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}
}
