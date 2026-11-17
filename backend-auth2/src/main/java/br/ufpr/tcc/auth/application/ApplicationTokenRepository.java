package br.ufpr.tcc.auth.application;

import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Leitura e gravacao dos tokens de aplicacao. */
public interface ApplicationTokenRepository extends JpaRepository<ApplicationToken, Long> {

    /**
     * Encontra a aplicacao dona de um token apresentado.
     *
     * A consulta exige, de uma vez, que o token exista, nao esteja revogado, nao esteja
     * vencido e pertenca a uma aplicacao ativa. As quatro condicoes vivem no mesmo WHERE de
     * proposito: separadas em caminhos, cada uma ganharia resposta propria, e a diferenca
     * entre elas e exatamente o que nao pode vazar.
     *
     * A busca e pelo digest, com restricao unica na coluna, porque o token nao carrega
     * identificador embutido.
     *
     * O relogio e o do banco, para que duas instancias nao discordem sobre quando o token
     * venceu.
     */
    @Query("""
            select new br.ufpr.tcc.auth.shared.security.ApplicationCaller(
                   application.id, application.code)
              from ApplicationToken token, RegisteredApplication application
             where application.id = token.applicationId
               and token.tokenDigest = :digest
               and token.revokedAt is null
               and (token.expiresAt is null or token.expiresAt > offset datetime)
               and application.active = true
            """)
    Optional<ApplicationCaller> resolveActiveCaller(@Param("digest") byte[] digest);

    List<ApplicationToken> findByApplicationIdOrderByCreatedAtDesc(long applicationId);

    long countByIdAndApplicationId(long id, long applicationId);

    /**
     * A marcacao so acontece se ainda nao tiver acontecido, para preservar o instante da
     * primeira revogacao, mas a resposta e a mesma nas duas chamadas: revogar o que ja esta
     * revogado e pedir o estado que ja vale.
     */
    @Modifying
    @Query("""
            update ApplicationToken token
               set token.revokedAt = offset datetime
             where token.id = :id and token.applicationId = :applicationId
               and token.revokedAt is null
            """)
    void revokeWithinApplication(@Param("id") long id,
            @Param("applicationId") long applicationId);
}
