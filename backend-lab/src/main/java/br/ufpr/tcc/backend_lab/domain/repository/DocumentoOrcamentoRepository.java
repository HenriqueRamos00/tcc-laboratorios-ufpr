package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface DocumentoOrcamentoRepository
        extends JpaRepository<DocumentoOrcamento, Long> {
    @Query(
            """
            select documento.orcamento.id
            from DocumentoOrcamento documento
            where documento.orcamento.id in :orcamentoIds
            """)
    List<Long> buscarIdsOrcamentosComDocumento(Collection<Long> orcamentoIds);
}
