package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.type.NumericBooleanConverter;

/**
 * Conta de uma pessoa dentro de uma aplicacao. A mesma pessoa em duas aplicacoes sao duas
 * contas, e e isso que permite o mesmo e-mail existir nas duas sem que uma enxergue a
 * outra.
 *
 * O e-mail e guardado duas vezes: como foi cadastrado, para exibir, e em forma minuscula,
 * que e sobre o que a unicidade e a busca acontecem. A forma normalizada e coluna e nao
 * indice de funcao porque o validate do Hibernate nao enxerga indice de funcao.
 *
 * Quem calcula a forma minuscula continua sendo o banco, agora pelo transformador de
 * coluna: a anotacao faz o Hibernate escrever LOWER(?) no INSERT e no UPDATE da coluna, do
 * mesmo jeito que o comando a mao escrevia. Isso nao e preferencia de estilo, e a diferenca
 * entre gravar e responder erro de servidor: a restricao do banco compara a coluna com
 * LOWER(EMAIL), e foi medido aqui que o I maiusculo com ponto, U+0130, desce em Java para
 * duas unidades de codigo e em Oracle para outra coisa. Calculando nos dois lugares, os dois
 * lados divergem e a restricao reprova a gravacao.
 *
 * A documentacao da anotacao avisa que, sem uma expressao de leitura que desfaca a de
 * escrita, o estado em memoria fica dessincronizado do banco depois do INSERT. Aqui isso nao
 * custa nada, porque nenhum codigo le a forma normalizada de volta: ela existe para a
 * unicidade e para o WHERE das consultas, que comparam com LOWER do parametro.
 */
@Entity
@Table(name = "CLIENT_ACCOUNT",
        uniqueConstraints = @UniqueConstraint(name = "UK_CLIENT_ACCOUNT_EMAIL",
                columnNames = {"APPLICATION_ID", "EMAIL_NORMALIZED"}),
        indexes = @Index(name = "IX_CLIENT_ACCOUNT_COMPANY", columnList = "APPLICATION_ID,COMPANY_ID"))
class ClientAccount {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "APPLICATION_ID")
    private Long applicationId;

    @Column(name = "COMPANY_ID")
    private Long companyId;

    @Column(name = "NAME", length = 150)
    private String name;

    @Column(name = "EMAIL", length = 254)
    private String email;

    @Column(name = "EMAIL_NORMALIZED", length = 254)
    @ColumnTransformer(write = "LOWER(?)")
    private String emailNormalized;

    @Column(name = "PASSWORD_HASH", length = 255)
    private String passwordHash;

    /**
     * As duas colunas de situacao sao NUMBER(1) e nao BOOLEAN, ao contrario das de
     * APPLICATION e de ADMINISTRATOR. A divergencia e divida registrada e nao escolha: trocar
     * o tipo exige migracao em tabela populada, e o caminho de conversao nao foi medido. O
     * conversor numerico do proprio Hibernate e o que faz o mapeamento enquanto isso durar.
     */
    @Column(name = "EMAIL_CONFIRMED")
    @Convert(converter = NumericBooleanConverter.class)
    private boolean emailConfirmed;

    @Column(name = "ACTIVE")
    @Convert(converter = NumericBooleanConverter.class)
    private boolean active;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    protected ClientAccount() {
    }

    /**
     * Conta nova nasce ativa e com o endereco por confirmar, que e a regra 7: ela existe,
     * mas o login recusa enquanto o link nao for usado.
     *
     * O identificador fica nulo de proposito, para o gerador do Hibernate atribui-lo antes do
     * INSERT. Instancia que ja chega com identificador faz o save() do Spring Data trata-la
     * como registro existente e cair em merge(), que emite um SELECT antes de cada INSERT.
     */
    static ClientAccount openFor(long applicationId, String name, String email,
            String passwordHash) {
        ClientAccount account = new ClientAccount();
        OffsetDateTime now = OffsetDateTime.now();
        account.applicationId = applicationId;
        account.name = name;
        account.email = email;
        account.emailNormalized = email;
        account.passwordHash = passwordHash;
        account.emailConfirmed = false;
        account.active = true;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    long id() {
        return id;
    }

    long applicationId() {
        return applicationId;
    }

    String name() {
        return name;
    }

    String email() {
        return email;
    }

    String passwordHash() {
        return passwordHash;
    }

    boolean emailConfirmed() {
        return emailConfirmed;
    }

    boolean active() {
        return active;
    }

    void rename(String name) {
        this.name = name;
        this.updatedAt = OffsetDateTime.now();
    }

    void situation(boolean active) {
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    void passwordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = OffsetDateTime.now();
    }

    void confirmEmail() {
        this.emailConfirmed = true;
        this.updatedAt = OffsetDateTime.now();
    }
}
