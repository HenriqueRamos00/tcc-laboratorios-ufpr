package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.NivelRisco;
import br.ufpr.tcc.backend_lab.domain.model.entity.TipoEquipamento;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipamentoRepository extends JpaRepository<Equipamento, Long> {
	@Query(value = """
			select e from Equipamento e
			where e.ativo = true and e.empresa.id = :empresaId
			  and (:busca = '' or lower(e.tag) like lower(concat('%', :busca, '%')) escape '!'
			       or lower(e.numeroSerie) like lower(concat('%', :busca, '%')) escape '!')
			""", countQuery = """
			select count(e) from Equipamento e
			where e.ativo = true and e.empresa.id = :empresaId
			  and (:busca = '' or lower(e.tag) like lower(concat('%', :busca, '%')) escape '!'
			       or lower(e.numeroSerie) like lower(concat('%', :busca, '%')) escape '!')
			""")
	Page<Equipamento> pesquisarAnalisados(@Param("empresaId") Long empresaId,
			@Param("busca") String busca, Pageable pageable);

	@Query("""
			select e from Equipamento e join fetch e.empresa empresa
			where e.ativo = true
			  and (:busca = ''
			       or locate(:busca, lower(e.numeroSerie)) > 0
			       or locate(:busca, lower(e.tag)) > 0
			       or locate(:busca, lower(empresa.legalName)) > 0)
			  and (:tipo is null or e.tipo = :tipo)
			  and (:risco is null or e.nivelRisco = :risco)
			order by e.numeroSerie asc, e.id asc
			""")
	List<Equipamento> pesquisarEquipamentos(
			@Param("busca") String busca,
			@Param("tipo") TipoEquipamento tipo,
			@Param("risco") NivelRisco risco
	);
}
