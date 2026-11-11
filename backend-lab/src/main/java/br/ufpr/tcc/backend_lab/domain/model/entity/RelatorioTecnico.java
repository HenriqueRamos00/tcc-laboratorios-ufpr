package br.ufpr.tcc.backend_lab.domain.model.entity;

import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "relatorios_tecnicos", indexes = {
        @Index(name = "idx_relatorios_execucao", columnList = "execucao_id"),
        @Index(name = "idx_relatorios_validacao_publicacao", columnList = "validation_status, published")
})
@Getter
@Setter
@NoArgsConstructor
public class RelatorioTecnico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "execucao_id", nullable = false)
    private ExecucaoEnsaio execucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_company_id")
    private Empresa validatedCompany;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_quote_id")
    private Orcamento validatedQuote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_sample_id")
    private Amostra validatedSample;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 30)
    private StatusValidacaoRelatorio validationStatus = StatusValidacaoRelatorio.PENDING_VALIDATION;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private Usuario validatedBy;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by_id")
    private Usuario publishedBy;

    @Column(name = "validated_snapshot", columnDefinition = "text")
    private String validatedSnapshot;

    @Column(name = "validated_search", length = 2000)
    private String validatedSearch;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void iniciarValoresPadrao() {
        if (validationStatus == null) {
            validationStatus = StatusValidacaoRelatorio.PENDING_VALIDATION;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
