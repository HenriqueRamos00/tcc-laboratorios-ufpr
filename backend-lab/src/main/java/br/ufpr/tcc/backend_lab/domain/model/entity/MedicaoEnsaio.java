package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medicoes_ensaios", uniqueConstraints = @UniqueConstraint(
        name = "uk_medicao_codigo_execucao", columnNames = {"execucao_id", "codigo"}))
@Getter
@Setter
@NoArgsConstructor
public class MedicaoEnsaio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "execucao_id", nullable = false)
    private ExecucaoEnsaio execucao;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false)
    private int posicao;

    @Column(length = 40)
    private String unidade;

    @Column(length = 255)
    private String metodo;

    @Column(name = "limite_referencia", length = 255)
    private String limiteReferencia;

    @Column(name = "tipo_resultado", nullable = false, length = 20)
    private String tipoResultado;

    @Column(precision = 24, scale = 8)
    private BigDecimal quantidade;

    @Column(name = "resultado_textual", length = 2000)
    private String resultadoTextual;
}
