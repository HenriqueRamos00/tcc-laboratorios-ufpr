package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.credential.model.PasswordRequirement;
import br.ufpr.tcc.auth.shared.identifier.IdentifierFactory;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Instalacao e entrada do administrador.
 *
 * A rota de instalacao exige duas coisas ao mesmo tempo, segredo de ambiente e ausencia de
 * administrador. Fechar so por estado deixaria uma janela entre a subida do contentor e a
 * primeira chamada do operador, e quem chegasse primeiro viraria dono do servico; fechar so
 * por segredo deixaria a rota viva para sempre.
 */
@Service
public class AdministratorAccess {

    private final AdministratorRegistry registry;
    private final AdministrativeSessionDirectory sessions;
    private final PasswordEncoder passwordEncoder;
    private final IdentifierFactory identifiers;
    private final AuditTrail auditTrail;
    private final String setupSecret;
    private final Duration sessionLifetime;
    private final String discardedHash;

    AdministratorAccess(AdministratorRegistry registry, AdministrativeSessionDirectory sessions,
            PasswordEncoder passwordEncoder, IdentifierFactory identifiers,
            AuditTrail auditTrail,
            @Value("${auth.setup.secret:}") String setupSecret,
            @Value("${auth.admin.session-lifetime:PT8H}") Duration sessionLifetime) {
        this.registry = registry;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.identifiers = identifiers;
        this.auditTrail = auditTrail;
        this.setupSecret = setupSecret;
        this.sessionLifetime = sessionLifetime;
        this.discardedHash = passwordEncoder.encode("senha-que-nao-autentica-ninguem");
    }

    /** Resultado da instalacao: ou o administrador nasceu, ou a rota nao existe. */
    public enum SetupOutcome { CREATED, ROUTE_DOES_NOT_EXIST, PASSWORD_REJECTED }

    @Transactional
    public SetupOutcome setupFirstAdministrator(String presentedSecret, String name, String email,
            String password) {
        if (setupSecret.isBlank() || !setupSecret.equals(presentedSecret)
                || !registry.noneRegistered()) {
            return SetupOutcome.ROUTE_DOES_NOT_EXIST;
        }
        Set<PasswordRequirement> violations = PasswordPolicy.violationsOf(password);
        if (!violations.isEmpty()) {
            return SetupOutcome.PASSWORD_REJECTED;
        }
        long identifier = identifiers.nextIdentifier();
        registry.insert(identifier, name, email, passwordEncoder.encode(password));
        return SetupOutcome.CREATED;
    }

    /** Sessao aberta, com o token em claro, que aparece uma vez so. */
    public record SignedIn(String accessToken, long expiresInSeconds, String name, String email,
            String id) {
    }

    /**
     * A senha e verificada sempre, inclusive quando o administrador nao existe, contra uma
     * forma guardada de descarte. Sem isso o tempo de resposta diria quais enderecos
     * administram o servico.
     */
    @Transactional
    public Optional<SignedIn> signIn(String email, String password) {
        Optional<AdministratorRegistry.StoredAdministrator> stored = registry.findByEmail(email);
        boolean matches = passwordEncoder.matches(password,
                stored.map(AdministratorRegistry.StoredAdministrator::passwordHash)
                        .orElse(discardedHash));
        if (!matches || stored.isEmpty() || !stored.get().active()) {
            auditTrail.administrativeSignInAttempted("recusada");
            return Optional.empty();
        }
        AdministratorRegistry.StoredAdministrator administrator = stored.get();
        auditTrail.administrativeSignInAttempted("aceita");
        return Optional.of(new SignedIn(
                sessions.open(administrator.id(), sessionLifetime).clearValue(),
                sessionLifetime.toSeconds(), administrator.name(), administrator.email(),
                Long.toString(administrator.id())));
    }
}
