package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documentos_relatorios")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoRelatorio {
    private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Id
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "relatorio_id", nullable = false)
    private RelatorioTecnico relatorio;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "pdf_valido")
    private Boolean pdfValido;

    @PrePersist
    @PreUpdate
    private void atualizarValidadePdf() {
        pdfValido = conteudoPdfValido(content);
    }

    public static boolean conteudoPdfValido(byte[] bytes) {
        if (bytes == null || bytes.length < ASSINATURA_PDF.length) {
            return false;
        }
        for (int index = 0; index < ASSINATURA_PDF.length; index++) {
            if (bytes[index] != ASSINATURA_PDF[index]) {
                return false;
            }
        }
        return true;
    }
}
