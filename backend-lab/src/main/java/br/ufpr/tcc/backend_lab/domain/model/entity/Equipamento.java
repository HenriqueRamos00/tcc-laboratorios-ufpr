package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "equipamentos")
@Getter
@Setter
@NoArgsConstructor
public class Equipamento {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String numeroSerie;

	@Column(nullable = false, length = 100)
	private String tag;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoEquipamento tipo;

	@Column(nullable = false, length = 150)
	private String subestacao;

	@Column(nullable = false, precision = 9, scale = 3)
	private BigDecimal tensaoPrimaria;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "empresa_id", nullable = false)
	private Empresa empresa;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NivelRisco nivelRisco;

	@Column(nullable = false)
	private boolean ativo = true;
}
