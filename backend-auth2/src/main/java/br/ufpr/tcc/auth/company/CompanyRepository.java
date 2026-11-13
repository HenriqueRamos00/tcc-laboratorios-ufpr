package br.ufpr.tcc.auth.company;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Leitura e gravacao da tabela de empresa.
 *
 * Nao existe escopo de aplicacao aqui porque a empresa nao pertence a aplicacao nenhuma:
 * quem chega a este repositorio ja passou pela cadeia de seguranca com credencial
 * administrativa, que atravessa aplicacoes.
 */
interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByOrderByCreatedAtDesc();

    long countByCnpj(String cnpj);

    /**
     * A alteracao e um comando proprio, e nao mexida na entidade carregada, para que a
     * contagem de linhas diga se a empresa existia no instante da gravacao.
     */
    @Modifying
    @Query("""
            update Company company
               set company.name = :name, company.cnpj = :cnpj, company.updatedAt = :now
             where company.id = :id
            """)
    int updateById(@Param("id") long id, @Param("name") String name,
            @Param("cnpj") String cnpj, @Param("now") OffsetDateTime now);

    /**
     * Conta vinculada impede a remocao pela chave estrangeira, e isso e o comportamento
     * desejado: apagar a empresa tiraria a autorizacao de quem a representa sem ninguem ter
     * pedido isso.
     */
    @Modifying
    @Query("delete from Company company where company.id = :id")
    int deleteWithId(@Param("id") long id);
}
