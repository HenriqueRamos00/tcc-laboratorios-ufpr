package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.credential.model.AdministrativeToken;
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
 * Sessao administrativa aberta, representada pelo digest do token entregue.
 *
 * A revogacao preenche a coluna e a linha fica, porque ela e trilha de auditoria. Apagar
 * acontece apenas quando o administrador e excluido, e ai por obrigacao: a chave estrangeira
 * aponta para ele e a exclusao falharia enquanto existisse sessao apontando.
 */
@Entity
@Table(name = "ADMINISTRATIVE_SESSION",
        uniqueConstraints = @UniqueConstraint(name = "UK_ADMINISTRATIVE_SESSION_DIGEST",
                columnNames = "TOKEN_DIGEST"),
        indexes = @Index(name = "IX_ADMINISTRATIVE_SESSION_ADMIN", columnList = "ADMINISTRATOR_ID"))
class AdministrativeSession {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADMINISTRATOR_ID")
    private Long administratorId;

    @Column(name = "TOKEN_DIGEST", length = 32)
    private byte[] tokenDigest;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "EXPIRES_AT")
    private OffsetDateTime expiresAt;

    @Column(name = "REVOKED_AT")
    private OffsetDateTime revokedAt;

    protected AdministrativeSession() {
    }

    static AdministrativeSession openedFor(long administratorId, AdministrativeToken token,
            Duration lifetime) {
        AdministrativeSession session = new AdministrativeSession();
        OffsetDateTime now = OffsetDateTime.now();
        session.administratorId = administratorId;
        session.tokenDigest = token.digest().asBytes();
        session.createdAt = now;
        session.expiresAt = now.plus(lifetime);
        return session;
    }
}
