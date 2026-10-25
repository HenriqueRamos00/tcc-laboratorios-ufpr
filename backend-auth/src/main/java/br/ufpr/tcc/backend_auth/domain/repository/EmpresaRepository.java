package br.ufpr.tcc.backend_auth.domain.repository;

import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    Optional<Empresa> findByCnpj(String cnpj);
    boolean existsByCnpj(String cnpj);
    boolean existsByCnpjAndIdNot(String cnpj, Long id);
    List<Empresa> findAllByOrderByNomeAscIdAsc();

    // Consulta apenas os vínculos, sem bloquear contas que estão autenticando.
    @Query(value = "select count(*) from usuarios_empresas where empresa_id = :id", nativeQuery = true)
    long contarVinculos(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Empresa e where e.id in :ids order by e.id")
    List<Empresa> obterParaVinculo(Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.id = :id")
    Optional<Empresa> bloquear(Long id);
}
