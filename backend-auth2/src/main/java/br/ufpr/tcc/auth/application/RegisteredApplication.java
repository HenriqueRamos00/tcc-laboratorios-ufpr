package br.ufpr.tcc.auth.application;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * Aplicacao do Lactec registrada para consumir este servico. O contrato publico chama isto
 * de service nas rotas e no JSON; o modelo chama de aplicacao, que e a palavra que o
 * cliente usa.
 *
 * A classe e de pacote de proposito: quem precisar dela trabalha dentro desta
 * funcionalidade, e o compilador recusa o acesso de fora em vez de deixar a regra para o
 * revisor.
 */
@Entity
@Table(name = "APPLICATION",
        uniqueConstraints = @UniqueConstraint(name = "UK_APPLICATION_CODE", columnNames = "CODE"))
class RegisteredApplication {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODE", length = 80)
    private String code;

    @Column(name = "NAME", length = 150)
    private String name;

    @Column(name = "ACTIVE")
    private boolean active;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    protected RegisteredApplication() {
    }
}
