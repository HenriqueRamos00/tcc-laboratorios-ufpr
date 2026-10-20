package br.ufpr.tcc.auth.auth.setup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import br.ufpr.tcc.auth.auth.model.AuthUser;
import br.ufpr.tcc.auth.auth.model.Role;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import br.ufpr.tcc.auth.auth.web.dto.SetupRequest;
import br.ufpr.tcc.auth.auth.web.dto.SetupStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SetupService {
    private final AuthUserRepository users;
    private final PasswordEncoder encoder;
    private final String setupToken;
    private final SetupAttemptLimiter limiter;

    public SetupService(AuthUserRepository users, PasswordEncoder encoder,
                        SetupAttemptLimiter limiter,
                        @Value("${auth.setup-token:}") String setupToken) {
        this.users = users;
        this.encoder = encoder;
        this.limiter = limiter;
        this.setupToken = setupToken == null ? "" : setupToken.strip();
        if (!this.setupToken.isEmpty() && this.setupToken.length() < 32) {
            throw new IllegalArgumentException("AUTH_SETUP_TOKEN must contain at least 32 characters");
        }
    }

    @Transactional(readOnly = true)
    public SetupStatusResponse status() {
        return new SetupStatusResponse(!users.existsByRole(Role.ADMIN), !setupToken.isEmpty());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void createFirstAdmin(String providedToken, String client, SetupRequest request) {
        limiter.checkAllowed(client);
        if (!tokenMatches(providedToken)) {
            limiter.recordFailure(client);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token de configuração inválido.");
        }
        if (users.existsByRole(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A configuração inicial já foi concluída.");
        }

        String name = request.name() == null ? "" : request.name().strip();
        String email = AuthUser.normalizeEmail(request.email());
        if (name.isBlank() || name.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome válido.");
        }
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado.");
        }
        if (request.password() == null || request.password().length() < 15
                || request.password().length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve conter entre 15 e 1024 caracteres.");
        }

        users.save(new AuthUser(UUID.randomUUID().toString(), name, email,
                encoder.encode(request.password()), Role.ADMIN));
        limiter.clear(client);
    }

    private boolean tokenMatches(String provided) {
        if (setupToken.isEmpty() || provided == null) return false;
        return MessageDigest.isEqual(setupToken.getBytes(StandardCharsets.UTF_8),
                provided.strip().getBytes(StandardCharsets.UTF_8));
    }
}
