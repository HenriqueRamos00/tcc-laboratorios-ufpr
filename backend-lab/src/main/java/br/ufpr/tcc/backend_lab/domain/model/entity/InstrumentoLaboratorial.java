package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "instrumentos_laboratoriais", uniqueConstraints =
        @UniqueConstraint(name = "uk_instrumentos_patrimonio", columnNames = "numero_patrimonio"))
@Getter
@Setter
@NoArgsConstructor
public class InstrumentoLaboratorial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_patrimonio", nullable = false, length = 80)
    private String numeroPatrimonio;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(length = 160)
    private String certificadoCalibracao;

    private LocalDate calibracaoValidaAte;
}
