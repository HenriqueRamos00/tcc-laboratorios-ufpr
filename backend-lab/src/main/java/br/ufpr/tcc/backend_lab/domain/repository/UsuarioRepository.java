package br.ufpr.tcc.backend_lab.domain.repository;

import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailNormalizado(String emailNormalizado);

	// Lista usuários do perfil informado, com busca opcional sem diferenciar maiúsculas de minúsculas
	// em nome, e-mail e unidade, filtro opcional por situação e ordenação estável por nome e ID.
	@Query("""
			select u from Usuario u
			where u.perfil = :perfil
			  and (:busca is null or :busca = ''
			       or lower(u.nome) like lower(concat('%', :busca, '%'))
			       or lower(u.email) like lower(concat('%', :busca, '%'))
			       or lower(coalesce(u.unidade, '')) like lower(concat('%', :busca, '%')))
			  and (:ativo is null or u.ativo = :ativo)
			order by u.nome asc, u.id asc
			""")
	List<Usuario> pesquisarTecnicos(
			@Param("perfil") PerfilUsuario perfil,
			@Param("busca") String busca,
			@Param("ativo") Boolean ativo
	);
}
