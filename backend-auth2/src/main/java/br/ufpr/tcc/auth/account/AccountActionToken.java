package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Token de uso unico para confirmar e-mail ou recuperar senha. Guarda somente o digest, a
 * finalidade e as datas. A migracao V008 garante um token pendente por conta e finalidade.
 */
@Entity
@Table(name = "ACCOUNT_CONFIRMATION",
        uniqueConstraints = @UniqueConstraint(name = "UK_ACCOUNT_CONFIRMATION_DIGEST",
                columnNames = "TOKEN_DIGEST"),
        indexes = @Index(name = "IX_ACCOUNT_CONFIRMATION_ACCOUNT", columnList = "CLIENT_ACCOUNT_ID"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccountActionToken {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "CLIENT_ACCOUNT_ID")
    private Long clientAccountId;

    @Column(name = "PURPOSE", length = 20)
    @Enumerated(EnumType.STRING)
    private OutboundMessage.Purpose purpose;

    @Column(name = "TOKEN_DIGEST", length = 32)
    private byte[] tokenDigest;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "EXPIRES_AT")
    private OffsetDateTime expiresAt;

    @Column(name = "USED_AT")
    private OffsetDateTime usedAt;

    static AccountActionToken issuedFor(long clientAccountId, ConfirmationToken token,
            OutboundMessage.Purpose purpose, Duration lifetime) {
        AccountActionToken confirmation = new AccountActionToken();
        OffsetDateTime now = OffsetDateTime.now();
        confirmation.clientAccountId = clientAccountId;
        confirmation.purpose = purpose;
        confirmation.tokenDigest = token.digest().asBytes();
        confirmation.createdAt = now;
        confirmation.expiresAt = now.plus(lifetime);
        return confirmation;
    }
}
