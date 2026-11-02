package br.ufpr.tcc.auth.application;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Leitura e gravacao da tabela de aplicacao registrada. */
interface RegisteredApplicationRepository extends JpaRepository<RegisteredApplication, Long> {

    List<RegisteredApplication> findByOrderByCreatedAtDesc();

    @Modifying
    @Query("""
            update RegisteredApplication application
               set application.name = :name, application.code = :code,
                   application.active = :active, application.updatedAt = :now
             where application.id = :id
            """)
    int updateById(@Param("id") long id, @Param("name") String name,
            @Param("code") String code, @Param("active") boolean active,
            @Param("now") OffsetDateTime now);

    /**
     * Excluir aplicacao e desativar, nao apagar.
     *
     * Apagar a linha deixaria conta de cliente e trilha de auditoria apontando para o nada,
     * e a trilha precisa continuar explicavel depois. A desativacao ja corta o acesso no
     * mesmo instante, porque a situacao e lida junto com a credencial.
     */
    @Modifying
    @Query("""
            update RegisteredApplication application
               set application.active = false, application.updatedAt = :now
             where application.id = :id
            """)
    int deactivateById(@Param("id") long id, @Param("now") OffsetDateTime now);
}
