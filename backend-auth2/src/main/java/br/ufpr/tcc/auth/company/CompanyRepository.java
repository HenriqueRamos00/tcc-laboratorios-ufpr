package br.ufpr.tcc.auth.company;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Leitura e gravacao da tabela de empresa.
 *
 * O escopo entra em toda assinatura e nao e opcional. Repositorio que aceite buscar empresa
 * so por identificador deixa o filtro por aplicacao virar escolha de quem chama, e e assim
 * que nasce uma consulta que enxerga cadastro alheio.
 */
interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByApplicationIdOrderByCreatedAtDesc(long applicationId);

    Optional<Company> findByIdAndApplicationId(long id, long applicationId);

    long countByApplicationIdAndCnpj(long applicationId, String cnpj);

    /**
     * A alteracao e um comando proprio, e nao mexida na entidade carregada, para que a
     * aplicacao de quem chamou continue no WHERE da gravacao. Mexer na entidade faria o
     * UPDATE sair apenas com a chave primaria.
     */
    @Modifying
    @Query("""
            update Company company
               set company.name = :name, company.cnpj = :cnpj, company.updatedAt = :now
             where company.id = :id and company.applicationId = :applicationId
            """)
    void updateWithinApplication(@Param("id") long id,
            @Param("applicationId") long applicationId, @Param("name") String name,
            @Param("cnpj") String cnpj, @Param("now") OffsetDateTime now);

    /**
     * Remove a empresa dentro da aplicacao dela. Conta vinculada impede a remocao pela
     * chave estrangeira, e isso e o comportamento desejado: apagar a empresa deixaria a
     * conta apontando para o nada.
     */
    @Modifying
    @Query("delete from Company company"
            + " where company.id = :id and company.applicationId = :applicationId")
    int deleteWithinApplication(@Param("id") long id,
            @Param("applicationId") long applicationId);
}
