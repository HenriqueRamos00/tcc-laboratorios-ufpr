package br.ufpr.tcc.auth.application;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Credencial que uma aplicacao apresenta para consumir este servico.
 *
 * O que fica guardado e o digest do token, nunca o valor emitido: quem perder o valor pede
 * outro, e ninguem, nem o administrador, consegue recuperar o anterior.
 *
 * A revogacao preenche a coluna e a linha fica, porque a auditoria exige saber quem revogou
 * e quando, e linha apagada nao conta essa historia.
 *
 * A classe e publica, ao contrario das outras entidades deste modulo, e o motivo e o preco
 * de ter trocado SQL por consulta tipada: a fatia de credencial precisa resolver o chamador
 * a partir do token apresentado, e para isso ela chama um metodo do repositorio desta fatia,
 * cuja assinatura menciona o tipo da entidade. Com SQL cru o tipo nao aparecia em assinatura
 * nenhuma. Nenhum pacote de fora le nem grava campo daqui: o que atravessa e so o metodo de
 * resolucao, e e nele que as quatro condicoes continuam juntas.
 */
@Entity
@Table(name = "APPLICATION_TOKEN",
        uniqueConstraints = @UniqueConstraint(name = "UK_APPLICATION_TOKEN_DIGEST",
                columnNames = "TOKEN_DIGEST"),
        indexes = @Index(name = "IX_APPLICATION_TOKEN_APPLICATION", columnList = "APPLICATION_ID"))
public class ApplicationToken {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "APPLICATION_ID")
    private Long applicationId;

    @Column(name = "NAME", length = 150)
    private String name;

    @Column(name = "TOKEN_DIGEST", length = 32)
    private byte[] tokenDigest;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "EXPIRES_AT")
    private OffsetDateTime expiresAt;

    @Column(name = "REVOKED_AT")
    private OffsetDateTime revokedAt;

    protected ApplicationToken() {
    }

    /** Prazo nulo e token sem vencimento, e a coluna aceita ausencia de valor por isso. */
    static ApplicationToken issuedFor(long applicationId, String name, ServiceToken token,
            Duration lifetime) {
        ApplicationToken issued = new ApplicationToken();
        OffsetDateTime now = OffsetDateTime.now();
        issued.applicationId = applicationId;
        issued.name = name;
        issued.tokenDigest = token.digest().asBytes();
        issued.createdAt = now;
        issued.expiresAt = lifetime == null ? null : now.plus(lifetime);
        return issued;
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

    OffsetDateTime revokedAt() {
        return revokedAt;
    }

    OffsetDateTime createdAt() {
        return createdAt;
    }

    OffsetDateTime expiresAt() {
        return expiresAt;
    }
}
