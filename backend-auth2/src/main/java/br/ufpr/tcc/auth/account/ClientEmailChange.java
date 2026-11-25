package br.ufpr.tcc.auth.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.NumericBooleanConverter;

/** Estado de troca pendente e janela de solicitacoes da conta. */
@Entity
@Table(name = "CLIENT_EMAIL_CHANGE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ClientEmailChange {

    @Id
    @Column(name = "CLIENT_ACCOUNT_ID")
    private Long accountId;

    @Column(name = "APPLICATION_ID", nullable = false)
    private Long applicationId;

    @Column(name = "NEW_EMAIL", length = 254)
    @Getter(AccessLevel.PACKAGE)
    private String newEmail;

    @Column(name = "CREATED_AT")
    @Getter(AccessLevel.PACKAGE)
    private OffsetDateTime createdAt;

    @Column(name = "EXPIRES_AT")
    @Getter(AccessLevel.PACKAGE)
    private OffsetDateTime expiresAt;

    @Column(name = "CURRENT_EMAIL_CONFIRMED")
    @Convert(converter = NumericBooleanConverter.class)
    private boolean currentEmailConfirmed;

    @Column(name = "NEW_EMAIL_CONFIRMED")
    @Convert(converter = NumericBooleanConverter.class)
    private boolean newEmailConfirmed;

    @Column(name = "CREDENTIAL_VERSION", nullable = false)
    private long credentialVersion;

    @Column(name = "WINDOW_STARTED_AT", nullable = false)
    private OffsetDateTime windowStartedAt;

    @Column(name = "REQUEST_COUNT", nullable = false)
    private int requestCount;

    static ClientEmailChange open(long accountId, long applicationId, OffsetDateTime now) {
        ClientEmailChange change = new ClientEmailChange();
        change.accountId = accountId;
        change.applicationId = applicationId;
        change.windowStartedAt = now;
        change.requestCount = 0;
        return change;
    }

    void recordRequest(OffsetDateTime now) {
        if (!windowStartedAt.plusMinutes(15).isAfter(now)) {
            windowStartedAt = now;
            requestCount = 0;
        }
        requestCount++;
    }

    boolean requestLimitReached(OffsetDateTime now) {
        if (!windowStartedAt.plusMinutes(15).isAfter(now)) {
            windowStartedAt = now;
            requestCount = 0;
        }
        return requestCount >= 3;
    }

    int requestCount() {
        return requestCount;
    }

    OffsetDateTime windowExpiresAt() {
        return windowStartedAt.plusMinutes(15);
    }

    boolean pending() {
        return newEmail != null;
    }

    long applicationId() {
        return applicationId;
    }

    String newEmail() {
        return newEmail;
    }

    OffsetDateTime expiresAt() {
        return expiresAt;
    }

    boolean currentEmailConfirmed() {
        return currentEmailConfirmed;
    }

    boolean newEmailConfirmed() {
        return newEmailConfirmed;
    }

    long credentialVersion() {
        return credentialVersion;
    }

    void start(String newEmail, long credentialVersion, OffsetDateTime now) {
        this.newEmail = newEmail;
        this.createdAt = now;
        this.expiresAt = now.plusHours(1);
        this.currentEmailConfirmed = false;
        this.newEmailConfirmed = false;
        this.credentialVersion = credentialVersion;
    }

    void confirmCurrentEmail() {
        currentEmailConfirmed = true;
    }

    void confirmNewEmail() {
        newEmailConfirmed = true;
    }

    void clearPending() {
        newEmail = null;
        createdAt = null;
        expiresAt = null;
        currentEmailConfirmed = false;
        newEmailConfirmed = false;
        credentialVersion = 0;
    }
}
