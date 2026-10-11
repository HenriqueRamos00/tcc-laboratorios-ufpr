package br.ufpr.tcc.backend_lab.application.dto.request;

import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EquipamentoRequest(
		@NotBlank(message = "Número de série é obrigatório")
		@Size(max = 100, message = "Número de série deve ter no máximo 100 caracteres")
		String serialNumber,

		@NotBlank(message = "TAG é obrigatória")
		@Size(max = 100, message = "TAG deve ter no máximo 100 caracteres")
		String tag,

		@NotNull(message = "Tipo é obrigatório")
		TipoEquipamento type,

		@NotBlank(message = "Subestação é obrigatória")
		@Size(max = 150, message = "Subestação deve ter no máximo 150 caracteres")
		String substation,

		@NotBlank(message = "Tensão primária é obrigatória")
		@Pattern(regexp = "[0-9]{1,6}(\\.[0-9]{1,3})?", message = "Tensão primária deve ter até seis dígitos inteiros e três decimais, usando ponto")
		@DecimalMin(value = "0", inclusive = false, message = "Tensão primária deve ser positiva")
		String primaryVoltage,

		@NotBlank(message = "Proprietário é obrigatório")
		@Size(max = 200, message = "Proprietário deve ter no máximo 200 caracteres")
		String owner,

		@NotNull(message = "Nível de risco é obrigatório")
		NivelRisco riskLevel
) {
	public EquipamentoRequest {
		serialNumber = serialNumber == null ? null : serialNumber.trim();
		tag = tag == null ? null : tag.trim();
		substation = substation == null ? null : substation.trim();
		primaryVoltage = primaryVoltage == null ? null : primaryVoltage.trim();
		owner = owner == null ? null : owner.trim();
	}
}
