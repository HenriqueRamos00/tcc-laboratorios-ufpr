package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * Conta de uma pessoa dentro de uma aplicacao. A mesma pessoa em duas aplicacoes sao duas
 * contas, e e isso que permite o mesmo e-mail existir nas duas sem que uma enxergue a
 * outra.
 *
 * O e-mail e guardado duas vezes: como foi cadastrado, para exibir, e em forma minuscula,
 * que e sobre o que a unicidade e a busca acontecem. A forma normalizada e coluna e nao
 * indice de funcao porque o validate do Hibernate nao enxerga indice de funcao.
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
    private String emailNormalized;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    protected ClientAccount() {
    }
}
