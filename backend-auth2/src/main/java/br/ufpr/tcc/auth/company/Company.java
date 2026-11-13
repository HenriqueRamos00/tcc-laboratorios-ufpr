package br.ufpr.tcc.auth.company;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * Empresa que uma conta de cliente pode representar.
 *
 * Ela nao pertence a aplicacao nenhuma: quem a cadastra e o administrador, pelo portal
 * administrativo, e a mesma pessoa juridica e uma empresa so para o servico inteiro. Por
 * isso o CNPJ e unico no servico, e contas de aplicacoes diferentes podem representar a
 * mesma empresa.
 */
@Entity
@Table(name = "COMPANY", uniqueConstraints =
        @UniqueConstraint(name = "UK_COMPANY_CNPJ", columnNames = "CNPJ"))
class Company {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

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
    static Company registered(String name, String cnpj) {
        Company company = new Company();
        OffsetDateTime now = OffsetDateTime.now();
        company.name = name;
        company.cnpj = cnpj;
        company.createdAt = now;
        company.updatedAt = now;
        return company;
    }

    long id() {
        return id;
    }

    String name() {
        return name;
    }

    String cnpj() {
        return cnpj;
    }
}
