package br.ufpr.tcc.auth.administrator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Leitura e gravacao de administradores. */
@Component
class AdministratorRegistry {

    private final JdbcClient jdbcClient;

    AdministratorRegistry(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    boolean noneRegistered() {
        return jdbcClient.sql("SELECT COUNT(*) FROM ADMINISTRATOR").query(Long.class)
                .single() == 0L;
    }

    void insert(long id, String name, String email, String passwordHash) {
        jdbcClient.sql("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :name, :email, LOWER(:email), TRUE, :hash, :now,"
                        + " :now)")
                .param("id", id).param("name", name)
                .param("email", withoutSurroundingSpace(email)).param("hash", passwordHash)
                .param("now", OffsetDateTime.now()).update();
    }

    record StoredAdministrator(long id, String name, String email, String passwordHash,
            boolean active) {
    }

    Optional<StoredAdministrator> findByEmail(String email) {
        return jdbcClient.sql("SELECT ID, NAME, EMAIL, PASSWORD_HASH, ACTIVE FROM ADMINISTRATOR"
                        + " WHERE EMAIL_NORMALIZED = LOWER(:email)")
                .param("email", withoutSurroundingSpace(email))
                .query((resultSet, rowNumber) -> new StoredAdministrator(
                        resultSet.getLong("ID"), resultSet.getString("NAME"),
                        resultSet.getString("EMAIL"), resultSet.getString("PASSWORD_HASH"),
                        resultSet.getBoolean("ACTIVE")))
                .optional();
    }

    record AdministratorView(String id, String name, String email, boolean active) {
    }

    List<AdministratorView> list() {
        return jdbcClient.sql("SELECT ID, NAME, EMAIL, ACTIVE FROM ADMINISTRATOR"
                        + " ORDER BY CREATED_AT DESC")
                .query(AdministratorRegistry::readView)
                .list();
    }

    Optional<AdministratorView> findById(long id) {
        return jdbcClient.sql("SELECT ID, NAME, EMAIL, ACTIVE FROM ADMINISTRATOR WHERE ID = :id")
                .param("id", id)
                .query(AdministratorRegistry::readView)
                .optional();
    }

    /**
     * O endereco e procurado pela forma normalizada, que e a mesma coluna que a restricao
     * unica do banco cobra. Comparar a forma apresentada deixaria passar o mesmo endereco
     * escrito em outra caixa, e a gravacao falharia depois, no banco, como erro de servidor.
     */
    boolean emailBelongsToAnother(String email, long id) {
        return findByEmail(email).filter(administrator -> administrator.id() != id).isPresent();
    }

    boolean update(long id, String name, String email, boolean active) {
        return jdbcClient.sql("UPDATE ADMINISTRATOR SET NAME = :name, EMAIL = :email,"
                        + " EMAIL_NORMALIZED = LOWER(:email), ACTIVE = :active,"
                        + " UPDATED_AT = :now WHERE ID = :id")
                .param("id", id).param("name", name)
                .param("email", withoutSurroundingSpace(email)).param("active", active)
                .param("now", OffsetDateTime.now()).update() == 1;
    }

    void updatePasswordHash(long id, String passwordHash) {
        jdbcClient.sql("UPDATE ADMINISTRATOR SET PASSWORD_HASH = :hash, UPDATED_AT = :now"
                        + " WHERE ID = :id")
                .param("id", id).param("hash", passwordHash).param("now", OffsetDateTime.now())
                .update();
    }

    boolean delete(long id) {
        return jdbcClient.sql("DELETE FROM ADMINISTRATOR WHERE ID = :id").param("id", id)
                .update() == 1;
    }

    /**
     * Quantos administradores ativos restariam se este saisse de cena. Zero significa que a
     * operacao em curso deixaria o servico sem ninguem para administrar, e a rota de
     * instalacao nao socorre: ela conta linha na tabela, e a linha desativada continua la.
     */
    long activeCountOtherThan(long id) {
        return jdbcClient.sql("SELECT COUNT(*) FROM ADMINISTRATOR WHERE ACTIVE = TRUE"
                        + " AND ID <> :id")
                .param("id", id).query(Long.class).single();
    }

    /**
     * A mesma contagem, depois de serializar as operacoes que mexem nela.
     *
     * Sem o bloqueio existe janela, e ela foi medida: no isolamento padrao do Oracle cada
     * comando le o que estava confirmado quando ele comecou, e contagem nao trava a linha que
     * leu. Duas requisicoes simultaneas, cada uma desativando um dos dois ultimos ativos,
     * contam uma a outra como ativa e as duas passam. O servico fica sem administrador ativo,
     * e esse estado nao tem volta pela API: a rota de instalacao conta linha na tabela, e a
     * linha desativada continua contando.
     *
     * O bloqueio e de tabela e nao de linha porque a regra fala do conjunto: a linha que
     * precisaria ser travada e a que a outra requisicao vai mexer, e qual e ela nao se sabe de
     * antemao. A tabela tem poucas linhas, a operacao e rara, e o modo exclusivo nao bloqueia
     * leitura. Isolamento serializavel nao resolve este caso, porque o Oracle recusa a
     * transacao concorrente quando as duas gravam a mesma linha, e aqui elas gravam linhas
     * diferentes.
     */
    long activeCountOtherThanUnderLock(long id) {
        jdbcClient.sql("LOCK TABLE ADMINISTRATOR IN EXCLUSIVE MODE").update();
        return activeCountOtherThan(id);
    }

    static Optional<Long> identifierOf(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notAnIdentifier) {
            return Optional.empty();
        }
    }

    /**
     * Apenas o espaco das pontas. Quem passa para minuscula e o banco, com a funcao LOWER,
     * e isso nao e preferencia de estilo.
     *
     * A coluna normalizada e conferida por uma restricao que compara com LOWER(EMAIL), e foi
     * medido aqui: o I maiusculo com ponto, U+0130, desce em Java para duas unidades de
     * codigo e em Oracle para outra coisa, entao calcular a minuscula nos dois lugares fazia
     * a restricao reprovar a gravacao e a rota responder erro de servidor onde o contrato
     * promete cadastro ou recusa. Com a minuscula saindo so do banco, os dois lados nao tem
     * como divergir.
     *
     * O corte de espaco alcanca apenas o que esta ate U+0020, e isto tambem foi medido neste
     * repositorio: o espaco nao quebravel U+00A0 e a largura zero U+200B sobrevivem tanto a
     * trim quanto a strip. Entao esta funcao deixa o endereco gravavel, e nao limpo.
     *
     * Nada disto alcanca senha. Senha nao passa por normalizacao nenhuma, porque cortar
     * espaco trocaria a credencial em silencio.
     */
    static String withoutSurroundingSpace(String email) {
        return email == null ? "" : email.trim();
    }

    private static AdministratorView readView(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new AdministratorView(Long.toString(resultSet.getLong("ID")),
                resultSet.getString("NAME"), resultSet.getString("EMAIL"),
                resultSet.getBoolean("ACTIVE"));
    }
}
