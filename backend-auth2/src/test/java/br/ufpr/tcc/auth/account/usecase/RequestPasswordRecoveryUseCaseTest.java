package br.ufpr.tcc.auth.account.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.sql.SQLException;
import java.time.Duration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class RequestPasswordRecoveryUseCaseTest {

    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final RequestPasswordRecoveryUseCase useCase = new RequestPasswordRecoveryUseCase(
            mock(ClientAccountStore.class), mock(MessageDelivery.class), transactions,
            Duration.ofMinutes(15));

    @Test
    void recoveryIgnoresOnlyThePendingTokenConstraint() {
        doThrow(violation("\"AUTH_OWNER\".\"UX_ACCOUNT_CONFIRMATION_PENDING\""))
                .when(transactions).executeWithoutResult(any());

        useCase.execute(mock(ApplicationCaller.class), "pessoa@example.com");
    }

    @Test
    void recoveryPropagatesOtherIntegrityFailures() {
        DataIntegrityViolationException failure = violation("UK_ACCOUNT_CONFIRMATION_DIGEST");
        doThrow(failure).when(transactions).executeWithoutResult(any());

        assertThatThrownBy(() -> useCase.execute(
                mock(ApplicationCaller.class), "pessoa@example.com")).isSameAs(failure);
    }

    private static DataIntegrityViolationException violation(String name) {
        return new DataIntegrityViolationException("Falha de integridade",
                new ConstraintViolationException("Restricao violada", new SQLException(), name));
    }
}
