package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /**
     * As duas condicoes sao avaliadas sempre, e a do segredo em tempo constante.
     *
     * Avaliar com curto-circuito deixava dois canais de tempo abertos. A comparacao
     * caractere a caractere parava no primeiro caractere diferente, dizendo quantos o
     * chamador ja acertou; e a consulta de administrador so acontecia quando o segredo
     * batia, entao segredo certo custava uma ida ao banco a mais que segredo errado, mesmo
     * depois da instalacao, quando as duas respostas sao 404. O 404 esconde o estado, o
     * relogio nao.
     */
    @Transactional
    public SetupOutcome setupFirstAdministrator(String presentedSecret, String name, String email,
            String password) {
        boolean secretMatches = secretMatches(presentedSecret);
        boolean withoutAdministrator = registry.noneRegistered();
        if (setupSecret.isBlank() || !secretMatches || !withoutAdministrator) {
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
     * Compara o segredo apresentado com o do ambiente sem que o tempo conte a diferenca.
     *
     * O digest entra porque os dois valores tem tamanhos diferentes, e comparar tamanho antes
     * ja seria um canal: com o resumo, a comparacao sempre percorre o mesmo numero de bytes.
     */
    private boolean secretMatches(String presentedSecret) {
        return MessageDigest.isEqual(digestOf(setupSecret),
                digestOf(presentedSecret == null ? "" : presentedSecret));
    }

    private static byte[] digestOf(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 precisa existir na plataforma", absent);
        }
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
     *
     * Esta entrada nao abre transacao, e isso e decisao e nao esquecimento. A verificacao de
     * senha custa 32 ms medidos, e com um limite transacional em volta dela a conexao do pool
     * ficava retida por esse tempo inteiro, numa rota aberta, para proteger um par de escritas
     * que nao precisa de atomicidade: no caminho de recusa so o contador grava, e ele ja e
     * atomico no proprio comando, com a corrida provada em teste que o chama sem transacao
     * nenhuma. No caminho de aceite gravam o contador e a sessao, e se a segunda falhasse o
     * resultado seria um contador zerado sem sessao aberta, que nao da acesso a ninguem. O
     * pool e o recurso mais escasso do processo, e a entrada de cliente, que e a rota quente,
     * ja funcionava assim.
     */
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
