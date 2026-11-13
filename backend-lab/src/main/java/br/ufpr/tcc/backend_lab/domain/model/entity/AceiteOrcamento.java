package br.ufpr.tcc.backend_lab.domain.model.entity;

import br.ufpr.tcc.backend_lab.domain.model.enums.FormaAceiteOrcamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Registro de que o cliente aceitou o orçamento. A chave primária é o próprio id do
 * orçamento, então o banco garante no máximo um aceite por orçamento.
 */
@Entity
@Table(name = "aceites_orcamentos")
@Getter
@Setter
@NoArgsConstructor
public class AceiteOrcamento {
    @Id private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "orcamento_id")
    private Orcamento orcamento;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FormaAceiteOrcamento method;

    @Column(nullable = false)
    private OffsetDateTime acceptedAt;
}
