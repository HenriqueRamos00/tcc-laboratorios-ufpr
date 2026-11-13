package br.ufpr.tcc.backend_lab.domain.model.entity;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.StatusEnsaioConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "historico_status_ensaios", indexes =
        @Index(name = "idx_historico_status_execucao_data", columnList = "execucao_id, alterado_em, id"))
@Getter
@Setter
@NoArgsConstructor
public class HistoricoStatusEnsaio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "execucao_id", nullable = false)
    private ExecucaoEnsaio execucao;

    @Convert(converter = StatusEnsaioConverter.class)
    @Column(name = "status_anterior", nullable = false, length = 30)
    private StatusEnsaio statusAnterior;

    @Convert(converter = StatusEnsaioConverter.class)
    @Column(name = "status_novo", nullable = false, length = 30)
    private StatusEnsaio statusNovo;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id", nullable = false)
    private Usuario tecnico;

    @Column(length = 2000)
    private String justificativa;
}
