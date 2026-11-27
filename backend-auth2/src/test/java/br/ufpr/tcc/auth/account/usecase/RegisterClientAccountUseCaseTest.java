package br.ufpr.tcc.auth.account.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

class RegisterClientAccountUseCaseTest {

    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final RegisterClientAccountUseCase useCase = new RegisterClientAccountUseCase(
            mock(ClientAccountStore.class), mock(PasswordEncoder.class),
            mock(MessageDelivery.class), mock(AuditTrail.class), transactions,
            Duration.ofHours(24));

    @Test
    void registrationTranslatesOnlyTheEmailConstraint() {
        when(transactions.execute(any())).thenThrow(violation("AUTH_OWNER.UK_CLIENT_ACCOUNT_EMAIL"));

        assertThat(useCase.execute(1L, "Pessoa", "pessoa@example.com", "SenhaForte123", List.of())
                .outcome()).isEqualTo(RegisterClientAccountUseCase.Result.Outcome.EMAIL_TAKEN);
    }

    @Test
    void registrationPropagatesOtherIntegrityFailures() {
        DataIntegrityViolationException failure = violation("FK_CLIENT_ACCOUNT_APPLICATION");
        when(transactions.execute(any())).thenThrow(failure);

        assertThatThrownBy(() -> useCase.execute(
                1L, "Pessoa", "pessoa@example.com", "SenhaForte123", List.of()))
                .isSameAs(failure);
    }

    private static DataIntegrityViolationException violation(String name) {
        return new DataIntegrityViolationException("Falha de integridade",
                new ConstraintViolationException("Restricao violada", new SQLException(), name));
    }
}
