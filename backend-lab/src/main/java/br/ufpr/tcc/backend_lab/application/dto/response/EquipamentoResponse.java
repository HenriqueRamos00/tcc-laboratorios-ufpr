package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;

public record EquipamentoResponse(
		Long id,
		String serialNumber,
		String tag,
		TipoEquipamento type,
		String substation,
		String primaryVoltage,
		String companyId,
		String owner,
		NivelRisco riskLevel
) {
	public static EquipamentoResponse fromEntity(Equipamento equipamento) {
		return new EquipamentoResponse(
				equipamento.getId(), equipamento.getNumeroSerie(), equipamento.getTag(),
				equipamento.getTipo(), equipamento.getSubestacao(),
				equipamento.getTensaoPrimaria().stripTrailingZeros().toPlainString(),
				equipamento.getEmpresa().getId().toString(),
				equipamento.getEmpresa().getLegalName(), equipamento.getNivelRisco()
		);
	}
}
