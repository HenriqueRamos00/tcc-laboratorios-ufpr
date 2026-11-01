package br.ufpr.tcc.backend_lab.domain.model.entity;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.StatusEnsaioConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Convert;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "execucoes_ensaios",
        indexes = @Index(name = "idx_execucoes_ensaios_orcamento_id", columnList = "orcamento_id"))
@Getter
@Setter
@NoArgsConstructor
public class ExecucaoEnsaio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "orcamento_id", nullable = false)
    private Orcamento orcamento;

    @Convert(converter = StatusEnsaioConverter.class)
    @Column(nullable = false, length = 32)
    private StatusEnsaio status;

    @Column(length = 255)
    private String reportReference;

    private OffsetDateTime publishedAt;
}
