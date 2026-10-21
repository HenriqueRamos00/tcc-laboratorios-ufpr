package br.ufpr.tcc.backend_auth.domain.repository;

import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ServicoRepository extends JpaRepository<Servico, Long> {
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    List<Servico> findAllByOrderByNomeAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from Servico s where s.id = :id")
    Optional<Servico> obterParaVinculo(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Servico s where s.id = :id")
    Optional<Servico> bloquear(Long id);
}
