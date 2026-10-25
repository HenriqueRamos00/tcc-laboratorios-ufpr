package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documentos_orcamentos")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoOrcamento {
    @Id private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "orcamento_id")
    private Orcamento orcamento;

    // TODO: migrar o conteúdo binário para o sistema de arquivos.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;
}
