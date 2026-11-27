package br.ufpr.tcc.auth.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AccountConstraintViolationTest {

    @Test
    void unknownConstraintIsNotInferredFromTheMessage() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("UK_CLIENT_ACCOUNT_EMAIL");
        assertThat(AccountConstraintViolation.matches(failure, "UK_CLIENT_ACCOUNT_EMAIL")).isFalse();
        assertThat(AccountConstraintViolation.matches(violation(null), "UK_CLIENT_ACCOUNT_EMAIL"))
                .isFalse();
    }

    private static DataIntegrityViolationException violation(String name) {
        return new DataIntegrityViolationException("Falha de integridade",
                new ConstraintViolationException("Restricao violada", new SQLException(), name));
    }
}
