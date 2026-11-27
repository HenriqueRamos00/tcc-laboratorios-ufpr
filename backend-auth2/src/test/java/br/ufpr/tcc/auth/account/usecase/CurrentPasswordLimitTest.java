package br.ufpr.tcc.auth.account.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.ufpr.tcc.auth.account.ClientAccount;
import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.account.ClientEmailChange;
import br.ufpr.tcc.auth.notification.EmailTransportAvailability;
import br.ufpr.tcc.auth.notification.MessageDelivery;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class CurrentPasswordLimitTest {

    @Test
    void trocaDeSenhaEEmailCompartilhamLimiteAntesDeVerificarASenha() {
        ClientAccountStore store = mock(ClientAccountStore.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        EmailTransportAvailability emailTransport = mock(EmailTransportAvailability.class);
        when(emailTransport.isEmailChangeAvailable()).thenReturn(true);
        CurrentPasswordVerification verification = new CurrentPasswordVerification(encoder);
        ChangePasswordUseCase changePassword = new ChangePasswordUseCase(store, encoder,
                verification, mock(AuditTrail.class), transactions);
        RequestEmailChangeUseCase requestEmailChange = new RequestEmailChangeUseCase(store,
                verification, mock(MessageDelivery.class), mock(AuditTrail.class), transactions,
                emailTransport, Duration.ofHours(1));
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
            assertThat(changePassword.execute(caller, "42", "incorreta", "NovaSenha123!")
                    .outcome()).isEqualTo(ChangePasswordUseCase.Outcome.CURRENT_PASSWORD_INCORRECT);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(requestEmailChange.execute(caller, "42", "novo@example.test", "incorreta")
                    .outcome()).isEqualTo(RequestEmailChangeUseCase.Outcome.CURRENT_PASSWORD_INCORRECT);
        }

        var senha = changePassword.execute(caller, "42", "incorreta", "NovaSenha123!");
        var email = requestEmailChange.execute(caller, "42", "novo@example.test", "incorreta");
        assertThat(senha.outcome()).isEqualTo(ChangePasswordUseCase.Outcome.RATE_LIMITED);
        assertThat(email.outcome()).isEqualTo(RequestEmailChangeUseCase.Outcome.RATE_LIMITED);
        assertThat(senha.retryAfterSeconds()).isBetween(1L, 900L);
        assertThat(email.retryAfterSeconds()).isBetween(1L, 900L);
        verify(encoder, times(5)).matches("incorreta", "hash");
    }
}
