package br.ufpr.tcc.auth.authentication;

import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Decide se um cliente de uma aplicacao entra.
 *
 * A ordem dos passos e parte do requisito, nao detalhe de implementacao:
 *
 * 1. O bloqueio e consultado antes de qualquer trabalho, e quando ele vale a senha nem
 *    chega a ser verificada.
 * 2. A senha e verificada **sempre**, inclusive quando a conta nao existe, contra uma forma
 *    guardada de descarte calculada na subida com o mesmo algoritmo e o mesmo custo. Sem
 *    isso o cronometro entrega o cadastro mesmo com a resposta sendo identica.
 * 3. Só depois as bandeiras de confirmacao e de situacao sao avaliadas. Avalia-las antes
 *    faria esses dois casos responderem rapido enquanto senha errada responde devagar, e
 *    tempo rapido passaria a significar que a conta existe e esta nesse estado.
 *
 * A busca e por aplicacao mais e-mail normalizado. Conta de outra aplicacao simplesmente
 * nao e encontrada, igual a conta que nao existe, entao esse vazamento fica impossivel por
 * construcao e nao por alguem ter lembrado de tratar.
 */
@Service
class AuthenticateClient {

    /**
     * Senha de descarte. Ela nunca autentica ninguem: existe so para que o caminho da
     * conta inexistente pague o mesmo custo de verificacao do caminho da conta real.
     */
    private static final String DISCARDED_PASSWORD = "senha-que-nao-autentica-ninguem";

    private final ClientCredentialDirectory credentials;
    private final LoginAttemptLedger ledger;
    private final PasswordEncoder passwordEncoder;
    private final Duration cooldown;
    private final AuditTrail auditTrail;
    private final String discardedHash;

    AuthenticateClient(ClientCredentialDirectory credentials, LoginAttemptLedger ledger,
            PasswordEncoder passwordEncoder, AuditTrail auditTrail,
            @Value("${auth.login.cooldown:PT15M}") Duration cooldown) {
        this.credentials = credentials;
        this.ledger = ledger;
        this.passwordEncoder = passwordEncoder;
        this.cooldown = cooldown;
        this.auditTrail = auditTrail;
        this.discardedHash = passwordEncoder.encode(DISCARDED_PASSWORD);
    }

    ClientAuthentication authenticate(ApplicationCaller caller, String email, String password) {
        String submittedEmail = withoutSurroundingSpace(email);
        LoginAttemptOutcome lock = ledger.currentOutcome(caller.applicationId(),
                submittedEmail);
        if (lock.locked()) {
            auditTrail.clientSignInAttempted(caller.applicationId(), "bloqueada");
            return new ClientAuthentication.Locked(lock.retryAfterSeconds());
        }

        Optional<ClientCredentialDirectory.StoredClientCredentialView> stored =
                credentials.findByApplicationAndEmail(caller.applicationId(),
                        submittedEmail);
        boolean passwordMatches = passwordEncoder.matches(
                password, stored.map(ClientCredentialDirectory.StoredClientCredentialView::passwordHash)
                        .orElse(discardedHash));

        if (passwordMatches && stored.isPresent() && isUsable(stored.get())) {
            ledger.registerSuccess(caller.applicationId(), submittedEmail);
            auditTrail.clientSignInAttempted(caller.applicationId(), "aceita");
            return new ClientAuthentication.Authenticated(identityOf(caller, stored.get()));
        }

        LoginAttemptOutcome afterFailure =
                ledger.registerFailure(caller.applicationId(), submittedEmail, cooldown);
        auditTrail.clientSignInAttempted(caller.applicationId(), "recusada");
        if (afterFailure.locked()) {
            auditTrail.clientLockedOut(caller.applicationId(), afterFailure.retryAfterSeconds());
        }
        return afterFailure.locked()
                ? new ClientAuthentication.Locked(afterFailure.retryAfterSeconds())
                : ClientAuthentication.rejected();
    }

    private static boolean isUsable(ClientCredentialDirectory.StoredClientCredentialView stored) {
        return stored.emailConfirmed() && stored.active();
    }

    private static ClientIdentity identityOf(ApplicationCaller caller,
            ClientCredentialDirectory.StoredClientCredentialView stored) {
        return new ClientIdentity(Long.toString(stored.id()), stored.name(), stored.email(),
                ClientIdentity.CLIENT_ROLE, caller.applicationCode(),
                List.copyOf(stored.companyCnpjs()));
    }

    /**
     * Apenas o espaco das pontas, porque quem passa para minuscula e o banco.
     *
     * A consulta compara a coluna normalizada com LOWER do valor submetido, entao a caixa
     * deixa de ser decidida em Java. O contador de tentativa recebe o mesmo valor e aplica a
     * propria minuscula na chave, que e um digest e nunca e comparado com o banco.
     */
    private static String withoutSurroundingSpace(String email) {
        return email == null ? "" : email.trim();
    }
}
