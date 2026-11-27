package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccount;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Confere a senha atual exigida pela troca de senha e pela troca de e-mail.
 *
 * As duas trocas compartilham o mesmo limite, cinco erros em quinze minutos, guardado na
 * propria conta. Sem limite, qualquer uma delas viraria um jeito de adivinhar a senha de quem
 * deixou a sessao aberta; com limites separados, a pessoa que adivinha ganharia o dobro de
 * tentativas. O bloqueio e consultado antes de conferir a senha, entao vale ate para a certa.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class CurrentPasswordVerification {

    private final PasswordEncoder passwordEncoder;

    /** Segundos ate a proxima tentativa permitida; zero quando a conta nao esta bloqueada. */
    long secondsUntilNextAttempt(ClientAccount account, OffsetDateTime now) {
        Duration wait = account.currentPasswordWait(now);
        return wait.toSeconds() + (wait.getNano() == 0 ? 0 : 1);
    }

    /** Acerto zera o contador de erros; erro conta para o bloqueio. */
    boolean matches(ClientAccount account, String currentPassword, OffsetDateTime now) {
        if (passwordEncoder.matches(currentPassword, account.passwordHash())) {
            account.clearCurrentPasswordFailures();
            return true;
        }
        account.recordCurrentPasswordFailure(now);
        return false;
    }
}
