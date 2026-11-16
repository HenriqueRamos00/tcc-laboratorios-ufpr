package br.ufpr.tcc.backend_lab.application.dto.request;

import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

		@Size(max = 150, message = "Fabricante deve ter no máximo 150 caracteres")
		String manufacturer,

		@Min(value = 1, message = "Ano de fabricação deve ser positivo")
		@Max(value = 9999, message = "Ano de fabricação deve ter no máximo quatro dígitos")
		Integer manufactureYear,

		@Pattern(regexp = "[0-9]{1,9}(\\.[0-9]{1,3})?", message = "Potência deve ser um número positivo em MVA, com até três casas decimais")
		@DecimalMin(value = "0", inclusive = false, message = "Potência deve ser positiva")
		String powerMva,

		@Size(max = 100, message = "Tipo de óleo deve ter no máximo 100 caracteres")
		String oilType,

		@Pattern(regexp = "[0-9]{1,9}(\\.[0-9]{1,3})?", message = "Volume de óleo deve ser um número não negativo em litros, com até três casas decimais")
		@DecimalMin(value = "0", message = "Volume de óleo não pode ser negativo")
		String oilVolumeLiters,

		@NotBlank(message = "Proprietário é obrigatório")
		@Pattern(regexp = "[1-9][0-9]{0,18}", message = "Identificador da empresa deve ser um número inteiro positivo")
		String companyId,

		@NotNull(message = "Nível de risco é obrigatório")
		NivelRisco riskLevel
) {
	public EquipamentoRequest {
		serialNumber = serialNumber == null ? null : serialNumber.trim();
		tag = tag == null ? null : tag.trim();
		substation = substation == null ? null : substation.trim();
		primaryVoltage = primaryVoltage == null ? null : primaryVoltage.trim();
		manufacturer = normalizarOpcional(manufacturer);
		oilType = normalizarOpcional(oilType);
		powerMva = normalizarOpcional(powerMva);
		oilVolumeLiters = normalizarOpcional(oilVolumeLiters);
	}

	private static String normalizarOpcional(String value) {
		if (value == null) return null;
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
