package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.event.PasswordRecoveryEmailRequested;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.PasswordRecoveryToken;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.PasswordRecoveryTokenRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class RequestPasswordRecoveryUseCase {

    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final PasswordRecoveryTokenRepository tokens;
    private final ApplicationEventPublisher events;

    @Value("${auth.recovery.lifetime}")
    private Duration lifetime;

    @Value("${auth.recovery.internal-url}")
    private String internalRecoveryUrl;

    @Value("${auth.recovery.client-url}")
    private String clientRecoveryUrl;

    @Transactional
    public void execute(String email) {
        Instant now = Instant.now();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        usuarios.findForPasswordRecovery(normalizedEmail)
                .filter(Usuario::isAtivo)
                .filter(this::isRecoverable)
                .ifPresent(user -> issueFor(user, now));
    }

    private boolean isRecoverable(Usuario user) {
        return user.getPerfil() != PerfilUsuario.CLIENTE || clientes.existsByUsuarioId(user.getId());
    }

    private void issueFor(Usuario user, Instant now) {
        tokens.consumePendingForUser(user.getId(), now);
        PasswordRecoveryToken.Issued issued =
                PasswordRecoveryToken.issue(user, now.plus(lifetime));
        tokens.save(issued.stored());
        String baseUrl = user.getPerfil() == PerfilUsuario.CLIENTE
                ? clientRecoveryUrl
                : internalRecoveryUrl;
        String link = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("token", issued.presentedToken())
                .build()
                .encode()
                .toUriString();
        events.publishEvent(new PasswordRecoveryEmailRequested(user.getEmail(), link));
    }
}
