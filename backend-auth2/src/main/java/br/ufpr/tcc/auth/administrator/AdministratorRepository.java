package br.ufpr.tcc.auth.administrator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Leitura e gravacao da tabela de administrador.
 *
 * O endereco e procurado pela forma normalizada, que e a mesma coluna que a restricao unica
 * do banco cobra, e quem passa o parametro para minuscula e o banco. Comparar a forma
 * apresentada deixaria passar o mesmo endereco escrito em outra caixa, e a gravacao falharia
 * depois, no banco, como erro de servidor.
 */
interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    @Query("""
            select administrator from Administrator administrator
             where administrator.emailNormalized = lower(:email)
            """)
    Optional<Administrator> findByEmail(@Param("email") String email);

    List<Administrator> findByOrderByCreatedAtDesc();

    @Modifying
    @Query("delete from Administrator administrator where administrator.id = :id")
    int deleteByIdentifier(@Param("id") long id);

    /**
     * Quantos administradores ativos restariam se este saisse de cena. Zero significa que a
     * operacao em curso deixaria o servico sem ninguem para administrar, e a rota de
     * instalacao nao socorre: ela conta linha na tabela, e a linha desativada continua la.
     */
    long countByActiveTrueAndIdNot(long id);

    /**
     * Serializa as operacoes que mexem na contagem de administrador ativo.
     *
     * Este e o unico comando do modulo que JPQL nao expressa, e ele continua aqui em SQL
     * porque a alternativa tipada nao resolve o caso. Bloqueio pessimista de consulta trava as
     * linhas que a consulta leu, e a linha que precisaria ser travada e a que a **outra**
     * requisicao vai mexer, que nao se sabe de antemao.
     *
     * Sem o bloqueio existe janela, e ela foi medida: no isolamento padrao do Oracle cada
     * comando le o que estava confirmado quando ele comecou, e contagem nao trava a linha que
     * leu. Duas requisicoes simultaneas, cada uma desativando um dos dois ultimos ativos,
     * contam uma a outra como ativa e as duas passam. O servico fica sem administrador ativo,
     * e esse estado nao tem volta pela API.
     *
     * O modo exclusivo nao bloqueia leitura, a tabela tem poucas linhas e a operacao e rara.
     * Isolamento serializavel nao resolve este caso, porque o Oracle recusa a transacao
     * concorrente quando as duas gravam a mesma linha, e aqui elas gravam linhas diferentes.
     */
    @Modifying
    @Query(value = "LOCK TABLE ADMINISTRATOR IN EXCLUSIVE MODE", nativeQuery = true)
    void lockTheWholeTable();
}
