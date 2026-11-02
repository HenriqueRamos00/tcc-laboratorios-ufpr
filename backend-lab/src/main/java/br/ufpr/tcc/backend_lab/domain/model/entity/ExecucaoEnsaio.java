package br.ufpr.tcc.backend_lab.domain.model.entity;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.StatusEnsaioConverter;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "execucoes_ensaios", uniqueConstraints =
        @UniqueConstraint(name = "uk_execucoes_codigo", columnNames = "codigo"),
        indexes = @Index(name = "idx_execucoes_orcamento", columnList = "orcamento_id"))
@Getter
@Setter
@NoArgsConstructor
public class ExecucaoEnsaio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40, unique = true)
    private String codigo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "amostra_id", nullable = false)
    private Amostra amostra;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "orcamento_id", nullable = false)
    private Orcamento orcamento;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Usuario responsavel;

    @Column(nullable = false, length = 200)
    private String tipo;

    @Column(name = "tipo_analise", length = 30)
    private String tipoAnalise;

    @Column(nullable = false, length = 255)
    private String metodo;

    @Column(nullable = false, length = 255)
    private String norma;

    @Column(name = "data_agendada", nullable = false)
    private LocalDate dataAgendada;

    @Column(name = "classificacao_analise", length = 20)
    private String classificacaoAnalise;

    @Column(name = "texto_laudo", length = 10000)
    private String textoLaudo;

    @Convert(converter = StatusEnsaioConverter.class)
    @Column(nullable = false, length = 30)
    private StatusEnsaio status = StatusEnsaio.INCOMPLETE;

    @Column(name = "report_reference", length = 255)
    private String reportReference;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToMany
    @JoinTable(name = "execucoes_instrumentos",
            joinColumns = @JoinColumn(name = "execucao_id"),
            inverseJoinColumns = @JoinColumn(name = "instrumento_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_execucao_instrumento",
                    columnNames = {"execucao_id", "instrumento_id"}))
    private Set<InstrumentoLaboratorial> instrumentos = new LinkedHashSet<>();

    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicao ASC")
    private List<MedicaoEnsaio> medicoes = new ArrayList<>();

    @PrePersist
    void iniciarValoresPadrao() {
        if (status == null) status = StatusEnsaio.INCOMPLETE;
        if (atualizadoEm == null) atualizadoEm = OffsetDateTime.now();
    }
}
