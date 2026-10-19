package br.ufpr.tcc.auth.auth.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import br.ufpr.tcc.auth.auth.model.Account;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> findByEmail(String email) {
        return jdbc.sql("SELECT * FROM auth_user WHERE email = :email")
                .param("email", Account.normalizeEmail(email)).query(Account.class).optional();
    }

    public Optional<Account> findById(String id) {
        return jdbc.sql("SELECT * FROM auth_user WHERE id = :id")
                .param("id", id).query(Account.class).optional();
    }

    public void insert(String id, String username, String email, String passwordHash,
                       String service, String serviceId) {
        jdbc.sql("""
                INSERT INTO auth_user (id, username, email, password_hash, service, service_id)
                VALUES (:id, :username, :email, :password, :service, :serviceId)
                """)
                .param("id", id).param("username", username)
                .param("email", email).param("password", passwordHash)
                .param("service", service).param("serviceId", serviceId).update();
    }

    public void recordFailedLogin(String email, LocalDateTime now, LocalDateTime lockedUntil) {
        jdbc.sql("""
                UPDATE auth_user SET
                    failed_attempts = CASE WHEN locked_until <= :now THEN 1 ELSE failed_attempts + 1 END,
                    locked_until = CASE WHEN locked_until <= :now THEN NULL
                        WHEN failed_attempts >= 4 THEN :until ELSE locked_until END
                WHERE email = :email
                """)
                .param("email", Account.normalizeEmail(email))
                .param("now", now).param("until", lockedUntil).update();
    }

    public void resetFailedLogins(String id) {
        jdbc.sql("UPDATE auth_user SET failed_attempts = 0, locked_until = NULL WHERE id = :id")
                .param("id", id).update();
    }
}
