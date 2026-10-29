package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.shared.security.AdministratorCaller;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Leitura e gravacao das sessoes administrativas. */
interface AdministrativeSessionRepository extends JpaRepository<AdministrativeSession, Long> {

    /**
     * Reconhece o administrador de uma sessao apresentada.
     *
     * A consulta exige de uma vez que o token exista, nao esteja revogado, nao esteja vencido
     * e pertenca a um administrador ativo. As quatro condicoes vivem no mesmo WHERE porque,
     * separadas, cada uma viraria um caminho com resposta propria.
     *
     * O relogio e o do banco, para que duas instancias nao discordem sobre quando a sessao
     * venceu.
     */
    @Query("""
            select new br.ufpr.tcc.auth.shared.security.AdministratorCaller(
                   administrator.id, administrator.name)
              from AdministrativeSession opened, Administrator administrator
             where administrator.id = opened.administratorId
               and opened.tokenDigest = :digest
               and opened.revokedAt is null
               and opened.expiresAt > offset datetime
               and administrator.active = true
            """)
    Optional<AdministratorCaller> resolveActiveCaller(@Param("digest") byte[] digest);

    /**
     * Fecha as sessoes abertas de um administrador sem apagar a linha.
     *
     * Usada na troca de senha e na desativacao: a senha muda porque a conta pode estar
     * comprometida, e deixar a sessao anterior de pe devolveria o acesso a quem a troca
     * deveria ter tirado.
     */
    @Modifying
    @Query("""
            update AdministrativeSession opened set opened.revokedAt = :now
             where opened.administratorId = :administratorId and opened.revokedAt is null
            """)
    void revokeOpenSessionsOf(@Param("administratorId") long administratorId,
            @Param("now") OffsetDateTime now);

    /**
     * Apaga as sessoes de um administrador que esta sendo excluido.
     *
     * Aqui a linha nao pode ficar, ao contrario da revogacao: a chave estrangeira aponta
     * para o administrador, e a exclusao dele falharia enquanto existir sessao apontando.
     */
    @Modifying
    @Query("delete from AdministrativeSession opened"
            + " where opened.administratorId = :administratorId")
    void deleteSessionsOf(@Param("administratorId") long administratorId);
}
