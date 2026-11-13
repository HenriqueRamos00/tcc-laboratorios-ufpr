package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoRelatorio;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface DocumentoRelatorioRepository extends JpaRepository<DocumentoRelatorio, Long> {
    Optional<DocumentoRelatorio> findByRelatorioId(Long reportId);

    @Query("select d.id from DocumentoRelatorio d where d.id in :reportIds and d.pdfValido = true")
    List<Long> buscarIdsComPdfValido(@Param("reportIds") Collection<Long> reportIds);
}
