package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Registro de que o cliente recusou o orçamento (HU-10). A justificativa é opcional. Assim
 * como no aceite, a chave primária é o id do orçamento.
 */
@Entity
@Table(name = "recusas_orcamentos")
@Getter
@Setter
@NoArgsConstructor
public class RecusaOrcamento {
    @Id private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "orcamento_id")
    private Orcamento orcamento;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(length = 200)
    private String justification;

    @Column(nullable = false)
    private OffsetDateTime refusedAt;
}
