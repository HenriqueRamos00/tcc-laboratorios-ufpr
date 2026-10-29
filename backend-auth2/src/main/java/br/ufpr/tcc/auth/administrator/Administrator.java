package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Administrador do servico. Ele atravessa aplicacoes, entao nao tem vinculo com nenhuma e
 * mora em tabela propria.
 *
 * Juntar administrador na tabela de conta com a coluna de aplicacao nula seria modelar nao
 * se aplica com ausencia de valor, e o e-mail dele e unico no servico inteiro, nao dentro
 * de uma aplicacao.
 *
 * Quem calcula a forma minuscula do e-mail e o banco, pelo transformador de coluna: a
 * anotacao faz o Hibernate escrever LOWER(?) no INSERT e no UPDATE da coluna. Isso nao e
 * preferencia de estilo, e a diferenca entre gravar e responder erro de servidor, porque a
 * restricao do banco compara a coluna com LOWER(EMAIL) e foi medido aqui que o I maiusculo
 * com ponto, U+0130, desce em Java para duas unidades de codigo e em Oracle para outra
 * coisa.
 */
@Entity
@Table(name = "ADMINISTRATOR",
        uniqueConstraints = @UniqueConstraint(name = "UK_ADMINISTRATOR_EMAIL",
                columnNames = "EMAIL_NORMALIZED"))
class Administrator {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", length = 150)
    private String name;

    @Column(name = "EMAIL", length = 254)
    private String email;

    @Column(name = "EMAIL_NORMALIZED", length = 254)
    @ColumnTransformer(write = "LOWER(?)")
    private String emailNormalized;

    @Column(name = "PASSWORD_HASH", length = 255)
    private String passwordHash;

    @Column(name = "ACTIVE")
    private boolean active;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    protected Administrator() {
    }

    /**
     * Administrador novo nasce ativo. O identificador fica nulo de proposito, para o gerador
     * do Hibernate atribui-lo antes do INSERT: instancia que ja chega com identificador faz o
     * save() do Spring Data trata-la como registro existente e cair em merge(), que emite um
     * SELECT antes de cada INSERT.
     */
    static Administrator registered(String name, String email, String passwordHash) {
        Administrator administrator = new Administrator();
        OffsetDateTime now = OffsetDateTime.now();
        administrator.name = name;
        administrator.email = email;
        administrator.emailNormalized = email;
        administrator.passwordHash = passwordHash;
        administrator.active = true;
        administrator.createdAt = now;
        administrator.updatedAt = now;
        return administrator;
    }

    long id() {
        return id;
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

    boolean active() {
        return active;
    }

    /**
     * Nome, endereco e situacao mudam juntos porque e isso que a rota de alteracao recebe, e
     * a forma minuscula acompanha o endereco: deixar as duas colunas desencontradas quebraria
     * a unicidade, que e cobrada sobre a normalizada.
     */
    void change(String name, String email, boolean active) {
        this.name = name;
        this.email = email;
        this.emailNormalized = email;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    void passwordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = OffsetDateTime.now();
    }
}
