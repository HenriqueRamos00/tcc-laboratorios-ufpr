package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.validation.PasswordRecoveryPasswordPolicy;
import br.ufpr.tcc.backend_lab.application.validation.SenhaValidator;
import br.ufpr.tcc.backend_lab.domain.model.entity.PasswordRecoveryToken;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.PasswordRecoveryTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResetPasswordUseCase {

    private final PasswordRecoveryTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final SenhaValidator senhaValidator;
    private final PasswordRecoveryPasswordPolicy recoveryPasswordPolicy;

    @Transactional
    public boolean execute(String presentedToken, String password) {
        senhaValidator.validar(password);
        recoveryPasswordPolicy.validate(password);

        Instant now = Instant.now();
        String digest = PasswordRecoveryToken.digest(presentedToken);
        return tokens.findForUpdate(digest)
                .filter(token -> token.isValidAt(now))
                .map(token -> reset(token, password, now))
                .orElse(false);
    }

    private boolean reset(PasswordRecoveryToken token, String password, Instant now) {
        Usuario user = token.getUser();
        token.consumeAt(now);
        tokens.consumePendingForUser(user.getId(), now);
        user.setSenhaHash(passwordEncoder.encode(password));
        user.setSenhaVersao(user.getSenhaVersaoAtual() + 1);
        return true;
    }
}
