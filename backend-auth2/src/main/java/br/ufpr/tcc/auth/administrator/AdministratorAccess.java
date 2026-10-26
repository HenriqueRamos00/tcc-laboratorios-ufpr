package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.credential.model.PasswordRequirement;
import br.ufpr.tcc.auth.authentication.LoginAttemptLedger;
import br.ufpr.tcc.auth.authentication.LoginAttemptOutcome;
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
    private final LoginAttemptLedger attempts;
    private final Duration loginCooldown;
    private final String setupSecret;
    private final Duration sessionLifetime;
    private final String discardedHash;

    AdministratorAccess(AdministratorRegistry registry, AdministrativeSessionDirectory sessions,
            PasswordEncoder passwordEncoder, IdentifierFactory identifiers,
            AuditTrail auditTrail, LoginAttemptLedger attempts,
            @Value("${auth.login.cooldown:PT15M}") Duration loginCooldown,
            @Value("${auth.setup.secret:}") String setupSecret,
            @Value("${auth.admin.session-lifetime:PT8H}") Duration sessionLifetime) {
        this.registry = registry;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.identifiers = identifiers;
        this.auditTrail = auditTrail;
        this.attempts = attempts;
        this.loginCooldown = loginCooldown;
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
        auditTrail.administratorChanged("installed", Long.toString(identifier));
        return SetupOutcome.CREATED;
    }

    /**
     * Desfecho da entrada administrativa.
     *
     * Ele tem tres estados e nao dois porque bloqueado nao e recusado: recusado responde 401
     * sem desafio, e bloqueado responde 429 com o prazo de espera, que e o que o consumidor
     * precisa para saber que nao e para tentar de novo agora.
     */
    public record AdministrativeSignIn(Outcome outcome, SignedIn session, long retryAfterSeconds) {

        public enum Outcome { SIGNED_IN, REJECTED, LOCKED }

        static AdministrativeSignIn rejected() {
            return new AdministrativeSignIn(Outcome.REJECTED, null, 0L);
        }

        static AdministrativeSignIn locked(long retryAfterSeconds) {
            return new AdministrativeSignIn(Outcome.LOCKED, null, retryAfterSeconds);
        }
    }

    /** Sessao aberta, com o token em claro, que aparece uma vez so. */
    public record SignedIn(String accessToken, long expiresInSeconds, String name, String email,
            String id) {
    }

    /**
     * A senha e verificada sempre, inclusive quando o administrador nao existe, contra uma
     * forma guardada de descarte. Sem isso o tempo de resposta diria quais enderecos
     * administram o servico.
     *
     * O contador de tentativa vale aqui tanto quanto no login de cliente, e por dois motivos.
     * A regra 3 do Lactec nao abre excecao para administrador, e esta e a porta que abre todas
     * as outras: quem entra aqui cadastra aplicacao, emite credencial e le a lista inteira. A
     * rota tambem e aberta na cadeia, sem credencial de aplicacao, entao tentativa ilimitada
     * nao exigiria nem estar integrado ao servico. E cada tentativa paga uma verificacao
     * dimensionada para custar caro, o que sem contador transforma adivinhacao de senha em
     * consumo de memoria do processo a pedido de quem chama.
     */
    @Transactional
    public AdministrativeSignIn signIn(String email, String password) {
        String identifier = email == null ? "" : email.trim();
        LoginAttemptOutcome lock =
                attempts.currentOutcome(LoginAttemptLedger.ADMINISTRATIVE_SCOPE, identifier);
        if (lock.locked()) {
            auditTrail.administrativeSignInAttempted("bloqueada");
            return AdministrativeSignIn.locked(lock.retryAfterSeconds());
        }
        Optional<AdministratorRegistry.StoredAdministrator> stored = registry.findByEmail(email);
        boolean matches = passwordEncoder.matches(password,
                stored.map(AdministratorRegistry.StoredAdministrator::passwordHash)
                        .orElse(discardedHash));
        if (!matches || stored.isEmpty() || !stored.get().active()) {
            LoginAttemptOutcome afterFailure = attempts.registerFailure(
                    LoginAttemptLedger.ADMINISTRATIVE_SCOPE, identifier, loginCooldown);
            auditTrail.administrativeSignInAttempted("recusada");
            if (afterFailure.locked()) {
                auditTrail.administrativeLockedOut(afterFailure.retryAfterSeconds());
                return AdministrativeSignIn.locked(afterFailure.retryAfterSeconds());
            }
            return AdministrativeSignIn.rejected();
        }
        AdministratorRegistry.StoredAdministrator administrator = stored.get();
        attempts.registerSuccess(LoginAttemptLedger.ADMINISTRATIVE_SCOPE, identifier);
        auditTrail.administrativeSignInAttempted("aceita");
        return new AdministrativeSignIn(AdministrativeSignIn.Outcome.SIGNED_IN, new SignedIn(
                sessions.open(administrator.id(), sessionLifetime).clearValue(),
                sessionLifetime.toSeconds(), administrator.name(), administrator.email(),
                Long.toString(administrator.id())), 0L);
    }
}
