package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.HistoricoStatusEnsaio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoricoStatusEnsaioRepository extends JpaRepository<HistoricoStatusEnsaio, Long> {
    @Query("""
            select h from HistoricoStatusEnsaio h join fetch h.tecnico
            where h.execucao.id = :execucaoId
            order by h.alteradoEm asc, h.id asc
            """)
    List<HistoricoStatusEnsaio> listarPorExecucao(@Param("execucaoId") Long execucaoId);
}
