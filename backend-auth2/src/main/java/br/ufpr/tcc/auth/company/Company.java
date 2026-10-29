package br.ufpr.tcc.auth.company;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * Empresa que uma conta de cliente pode representar. Ela pertence a uma aplicacao, e e por
 * isso que o CNPJ e unico dentro da aplicacao e nao no servico inteiro.
 *
 * O vinculo com a aplicacao viaja como identificador e nao como associacao: a forma de
 * expressar a chave estrangeira composta pertence a fatia que precisar navegar por ela.
 */
@Entity
@Table(name = "COMPANY", uniqueConstraints = {
        @UniqueConstraint(name = "UK_COMPANY_APPLICATION_ID", columnNames = {"APPLICATION_ID", "ID"}),
        @UniqueConstraint(name = "UK_COMPANY_CNPJ", columnNames = {"APPLICATION_ID", "CNPJ"})})
class Company {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "APPLICATION_ID")
    private Long applicationId;

    @Column(name = "NAME", length = 150)
    private String name;

    @Column(name = "CNPJ", length = 14)
    private String cnpj;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    protected Company() {
    }

    /**
     * O identificador fica nulo de proposito, para o gerador do Hibernate atribui-lo antes
     * do INSERT. Instancia que ja chega com identificador faz o save() do Spring Data
     * trata-la como registro existente e cair em merge(), que emite um SELECT antes de cada
     * INSERT.
     */
    static Company registeredIn(long applicationId, String name, String cnpj) {
        Company company = new Company();
        OffsetDateTime now = OffsetDateTime.now();
        company.applicationId = applicationId;
        company.name = name;
        company.cnpj = cnpj;
        company.createdAt = now;
        company.updatedAt = now;
        return company;
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

    String cnpj() {
        return cnpj;
    }
}
