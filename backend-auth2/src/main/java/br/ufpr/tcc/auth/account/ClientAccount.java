package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.type.NumericBooleanConverter;

/**
 * Conta pertencente a uma aplicacao. O e-mail e unico por aplicacao e sua forma normalizada e
 * calculada pelo Oracle com LOWER, evitando divergencia entre Java e banco.
 */
@Entity
@Table(name = "CLIENT_ACCOUNT",
        uniqueConstraints = @UniqueConstraint(name = "UK_CLIENT_ACCOUNT_EMAIL",
                columnNames = {"APPLICATION_ID", "EMAIL_NORMALIZED"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Accessors(fluent = true)
class ClientAccount {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "APPLICATION_ID")
    private Long applicationId;

    @Column(name = "NAME", length = 150)
    @Getter(AccessLevel.PACKAGE)
    private String name;

    @Column(name = "EMAIL", length = 254)
    @Getter(AccessLevel.PACKAGE)
    private String email;

    @Column(name = "EMAIL_NORMALIZED", length = 254)
    @ColumnTransformer(write = "LOWER(?)")
    private String emailNormalized;

    @Column(name = "PASSWORD_HASH", length = 255)
    @Getter(AccessLevel.PACKAGE)
    private String passwordHash;

    /** O esquema armazena estas flags como NUMBER(1); o conversor faz a ponte com boolean. */
    @Column(name = "EMAIL_CONFIRMED")
    @Convert(converter = NumericBooleanConverter.class)
    @Getter(AccessLevel.PACKAGE)
    private boolean emailConfirmed;

    @Column(name = "ACTIVE")
    @Convert(converter = NumericBooleanConverter.class)
    @Getter(AccessLevel.PACKAGE)
    private boolean active;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    /**
     * Cria conta ativa com e-mail ainda nao confirmado. O ID fica nulo para o Hibernate gera-lo no
     * INSERT.
     */
    static ClientAccount openFor(long applicationId, String name, String email,
            String passwordHash) {
        ClientAccount account = new ClientAccount();
        OffsetDateTime now = OffsetDateTime.now();
        account.applicationId = applicationId;
        account.name = name;
        account.email = email;
        account.emailNormalized = email;
        account.passwordHash = passwordHash;
        account.emailConfirmed = false;
        account.active = true;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    long id() {
        return id;
    }

    long applicationId() {
        return applicationId;
    }

    void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = OffsetDateTime.now();
    }

    void confirmEmail() {
        this.emailConfirmed = true;
        this.updatedAt = OffsetDateTime.now();
    }
}
