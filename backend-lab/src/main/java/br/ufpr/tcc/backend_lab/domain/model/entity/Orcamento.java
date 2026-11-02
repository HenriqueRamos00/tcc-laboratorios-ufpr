package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.EtapaAcompanhamentoOrcamento;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orcamentos")
@Getter
@Setter
@NoArgsConstructor
public class Orcamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 80, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 80)
    private StatusComercialOrcamento status;

    @Column(nullable = false, length = 120)
    private String stage;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 120)
    private String laboratoryName;

    @Column(length = 2000)
    private String scope;

    @Column(length = 150)
    private String externalContactName;

    @Column(length = 254)
    private String externalContactEmail;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false)
    private OffsetDateTime createdDate;

    @OneToMany(mappedBy = "orcamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<OrcamentoItem> items = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void validarEtapa() {
        EtapaAcompanhamentoOrcamento.deTexto(stage);
    }

    /** HU-09: o cliente aprova a proposta e o orçamento segue para execução. */
    public void registrarAceite() {
        exigirAguardandoDecisaoDoCliente();
        status = StatusComercialOrcamento.IN_PROGRESS;
        stage = EtapaAcompanhamentoOrcamento.APROVADO_PELO_CLIENTE.texto();
    }

    /** HU-10: o cliente recusa a proposta e o orçamento é encerrado. */
    public void registrarRecusa() {
        exigirAguardandoDecisaoDoCliente();
        status = StatusComercialOrcamento.REFUSED;
        stage = EtapaAcompanhamentoOrcamento.RECUSADO.texto();
    }

    // O cliente só decide uma proposta que já foi escrita e ainda não foi decidida: o
    // orçamento precisa estar pendente de aceite e na etapa de negociação.
    private void exigirAguardandoDecisaoDoCliente() {
        boolean aguardandoDecisao =
                status == StatusComercialOrcamento.PENDING_ACCEPTANCE
                        && EtapaAcompanhamentoOrcamento.deTexto(stage)
                                == EtapaAcompanhamentoOrcamento.NEGOCIACAO;
        if (!aguardandoDecisao) {
            throw RegraNegocioException.conflito(
                    "Este orçamento não está aguardando a decisão do cliente");
        }
    }
}
