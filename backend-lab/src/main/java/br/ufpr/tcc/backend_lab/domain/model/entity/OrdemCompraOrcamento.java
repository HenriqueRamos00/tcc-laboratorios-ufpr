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

/**
 * Arquivo da ordem de compra (PDF, DOC ou DOCX) enviado pelo cliente no aceite. Fica em
 * tabela própria, como {@link DocumentoOrcamento}, para que consultar o aceite não carregue
 * os bytes do arquivo.
 */
@Entity
@Table(name = "ordens_compra_orcamentos")
@Getter
@Setter
@NoArgsConstructor
public class OrdemCompraOrcamento {
    @Id private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "aceite_id")
    private AceiteOrcamento aceite;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;
}
