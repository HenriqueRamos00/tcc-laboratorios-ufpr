package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * Administrador do servico. Ele atravessa aplicacoes, entao nao tem vinculo com nenhuma e
 * mora em tabela propria.
 *
 * Juntar administrador na tabela de conta com a coluna de aplicacao nula seria modelar nao
 * se aplica com ausencia de valor, e o e-mail dele e unico no servico inteiro, nao dentro
 * de uma aplicacao.
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
    private String emailNormalized;

    @Column(name = "ACTIVE")
    private boolean active;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    protected Administrator() {
    }
}
