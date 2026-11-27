package br.ufpr.tcc.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ClientAccountServiceTest {

    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ClientAccountService service = new ClientAccountService(
            mock(ClientAccountStore.class), mock(PasswordEncoder.class),
            mock(MessageDelivery.class), mock(AuditTrail.class), transactions,
            Duration.ofHours(24), Duration.ofMinutes(15));

    @Test
    void registrationTranslatesOnlyTheEmailConstraint() {
        when(transactions.execute(any())).thenThrow(violation("AUTH_OWNER.UK_CLIENT_ACCOUNT_EMAIL"));

        assertThat(service.register(1L, "Pessoa", "pessoa@example.com", "SenhaForte123", List.of())
                .outcome()).isEqualTo(ClientAccountService.Registration.Outcome.EMAIL_TAKEN);
    }

    @Test
    void registrationPropagatesOtherIntegrityFailures() {
        DataIntegrityViolationException failure = violation("FK_CLIENT_ACCOUNT_APPLICATION");
        when(transactions.execute(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.register(
                1L, "Pessoa", "pessoa@example.com", "SenhaForte123", List.of()))
                .isSameAs(failure);
    }

    @Test
    void recoveryIgnoresOnlyThePendingTokenConstraint() {
        doThrow(violation("\"AUTH_OWNER\".\"UX_ACCOUNT_CONFIRMATION_PENDING\""))
                .when(transactions).executeWithoutResult(any());

        service.requestPasswordRecovery(mock(ApplicationCaller.class), "pessoa@example.com");
    }

    @Test
    void recoveryPropagatesOtherIntegrityFailures() {
        DataIntegrityViolationException failure = violation("UK_ACCOUNT_CONFIRMATION_DIGEST");
        doThrow(failure).when(transactions).executeWithoutResult(any());

        assertThatThrownBy(() -> service.requestPasswordRecovery(
                mock(ApplicationCaller.class), "pessoa@example.com")).isSameAs(failure);
    }

    @Test
    void trocaDeSenhaEEmailCompartilhamLimiteAntesDeVerificarASenha() {
        ClientAccountStore store = mock(ClientAccountStore.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ClientAccountService profile = new ClientAccountService(store, encoder,
                mock(MessageDelivery.class), mock(AuditTrail.class), transactions,
                Duration.ofHours(24), Duration.ofMinutes(15));
        ApplicationCaller caller = mock(ApplicationCaller.class);
        when(caller.applicationId()).thenReturn(1L);
        when(transactions.execute(any())).thenAnswer(call ->
                call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));

        ClientAccount account = ClientAccount.openFor(1L, "Pessoa", "pessoa@example.test", "hash");
        account.confirmEmail();
        ReflectionTestUtils.setField(account, "id", 42L);
        when(store.lockedAccount(1L, 42L)).thenReturn(Optional.of(account));
        when(store.emailChange(42L, 1L)).thenReturn(ClientEmailChange.open(42L, 1L, OffsetDateTime.now()));

        for (int i = 0; i < 3; i++) {
            assertThat(profile.changeSelfServicePassword(caller, "42", "incorreta", "NovaSenha123!")
                    .outcome()).isEqualTo(ClientAccountService.SelfServiceOutcome.CURRENT_PASSWORD_INCORRECT);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(profile.requestEmailChange(caller, "42", "novo@example.test", "incorreta")
                    .outcome()).isEqualTo(ClientAccountService.EmailChangeOutcome.CURRENT_PASSWORD_INCORRECT);
        }

        var senha = profile.changeSelfServicePassword(caller, "42", "incorreta", "NovaSenha123!");
        var email = profile.requestEmailChange(caller, "42", "novo@example.test", "incorreta");
        assertThat(senha.outcome()).isEqualTo(ClientAccountService.SelfServiceOutcome.RATE_LIMITED);
        assertThat(email.outcome()).isEqualTo(ClientAccountService.EmailChangeOutcome.RATE_LIMITED);
        assertThat(senha.retryAfterSeconds()).isBetween(1L, 900L);
        assertThat(email.retryAfterSeconds()).isBetween(1L, 900L);
        verify(encoder, times(5)).matches("incorreta", "hash");
    }

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
