package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "password_recovery_tokens",
        indexes = @Index(name = "idx_password_recovery_user", columnList = "user_id"),
        uniqueConstraints =
                @UniqueConstraint(name = "uk_password_recovery_digest", columnNames = "token_digest"))
@Getter
@NoArgsConstructor
public class PasswordRecoveryToken {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Usuario user;

    @Column(name = "token_digest", nullable = false, length = 64, unique = true)
    private String tokenDigest;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    private PasswordRecoveryToken(Usuario user, String tokenDigest, Instant expiresAt) {
        this.user = user;
        this.tokenDigest = tokenDigest;
        this.expiresAt = expiresAt;
    }

    public static Issued issue(Usuario user, Instant expiresAt) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String presentedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordRecoveryToken stored =
                new PasswordRecoveryToken(user, digest(presentedToken), expiresAt);
        return new Issued(presentedToken, stored);
    }

    public static String digest(String presentedToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(presentedToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    public boolean isValidAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now) && user.isAtivo();
    }

    public void consumeAt(Instant now) {
        consumedAt = now;
    }

    public record Issued(String presentedToken, PasswordRecoveryToken stored) {
    }
}
