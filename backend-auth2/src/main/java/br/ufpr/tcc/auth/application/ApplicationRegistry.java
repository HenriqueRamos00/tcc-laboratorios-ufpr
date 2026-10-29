package br.ufpr.tcc.auth.application;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastro de aplicacoes e emissao dos tokens delas.
 *
 * O token emitido volta em claro uma unica vez, no corpo da resposta da emissao. O que
 * fica guardado e o digest: quem perder o valor pede outro, e ninguem, nem o
 * administrador, consegue recuperar o anterior.
 *
 * Cadastro e alteracao abrem a transacao pelo modelo e nao pela anotacao, porque as duas
 * disputam a restricao unica do codigo e precisam traduzir a recusa dela para conflito. A
 * recusa chega como excecao de integridade e o provedor ja marcou a transacao para desfazer,
 * entao tratar isso dentro dela faria o commit falhar com erro de servidor onde o contrato
 * promete 409.
 */
@Service
public class ApplicationRegistry {

    private final RegisteredApplicationRepository applications;
    private final ApplicationTokenRepository tokens;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    ApplicationRegistry(RegisteredApplicationRepository applications,
            ApplicationTokenRepository tokens, AuditTrail auditTrail,
            TransactionTemplate transactions) {
        this.applications = applications;
        this.tokens = tokens;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
    }

    public record RegisteredApplicationView(String id, String name, String code, boolean active) {
    }

    /** Desfecho do cadastro de aplicacao, com o conflito de codigo separado do sucesso. */
    public record Registration(Outcome outcome, RegisteredApplicationView application) {

        public enum Outcome { CREATED, CODE_ALREADY_REGISTERED }
    }

    private static RegisteredApplicationView toView(RegisteredApplication application) {
        return new RegisteredApplicationView(Long.toString(application.id()), application.name(),
                application.code(), application.active());
    }

    /**
     * O codigo e unico no banco, e o segundo cadastro com o mesmo codigo precisa responder
     * conflito. Medido antes desta traducao: a violacao da restricao subia como erro de
     * servidor, que nao esta na matriz de codigos e que no consumidor vira fonte
     * indisponivel. O 409 e aceitavel aqui porque quem chama ja pode ler a lista inteira de
     * aplicacoes na rota ao lado, entao a resposta nao conta nada novo.
     */
    public Registration register(String name, String code) {
        try {
            return transactions.execute(status -> {
                RegisteredApplication registered = applications
                        .saveAndFlush(RegisteredApplication.registered(name, code));
                auditTrail.applicationChanged("registered", Long.toString(registered.id()));
                return new Registration(Registration.Outcome.CREATED, toView(registered));
            });
        } catch (DataIntegrityViolationException codeAlreadyRegistered) {
            return new Registration(Registration.Outcome.CODE_ALREADY_REGISTERED, null);
        }
    }

    public record IssuedTokenView(String accessToken, String id, String name, String serviceId,
            OffsetDateTime createdAt, OffsetDateTime expiresAt) {
    }

    @Transactional
    public IssuedTokenView issueToken(long applicationId, String name, Duration lifetime) {
        ServiceToken token = ServiceToken.generate();
        ApplicationToken issued = tokens
                .saveAndFlush(ApplicationToken.issuedFor(applicationId, name, token, lifetime));
        auditTrail.applicationTokenChanged("issued", Long.toString(applicationId),
                Long.toString(issued.id()));
        return new IssuedTokenView(token.clearValue(), Long.toString(issued.id()), name,
                Long.toString(applicationId), issued.createdAt(), issued.expiresAt());
    }

    /** Metadado do token. O segredo nao aparece aqui, porque ele nao existe mais guardado. */
    public record TokenView(String id, String name, String serviceId, OffsetDateTime createdAt,
            OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    }

    public List<TokenView> listTokens(long applicationId) {
        return tokens.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(token -> new TokenView(Long.toString(token.id()), token.name(),
                        Long.toString(token.applicationId()), token.createdAt(),
                        token.expiresAt(), token.revokedAt()))
                .toList();
    }

    /**
     * Revoga o token dentro da aplicacao dele.
     *
     * A existencia e conferida com o escopo junto, entao token de outra aplicacao e token
     * inventado produzem a mesma resposta: 403 ali confirmaria que o token existe em
     * algum lugar.
     */
    @Transactional
    public boolean revokeToken(long applicationId, long tokenId) {
        if (tokens.countByIdAndApplicationId(tokenId, applicationId) == 0L) {
            return false;
        }
        tokens.revokeWithinApplication(tokenId, applicationId);
        auditTrail.applicationTokenChanged("revoked", Long.toString(applicationId),
                Long.toString(tokenId));
        return true;
    }

    public List<RegisteredApplicationView> list() {
        return applications.findByOrderByCreatedAtDesc().stream()
                .map(ApplicationRegistry::toView).toList();
    }

    public Optional<RegisteredApplicationView> find(long applicationId) {
        return applications.findById(applicationId).map(ApplicationRegistry::toView);
    }

    /** Desfecho da alteracao, com o conflito de codigo separado de nao encontrado. */
    public enum UpdateOutcome { CHANGED, NOT_FOUND, CODE_ALREADY_REGISTERED }

    public UpdateOutcome update(long applicationId, String name, String code, boolean active) {
        try {
            return transactions.execute(status -> {
                if (applications.updateById(applicationId, name, code, active,
                        OffsetDateTime.now()) != 1) {
                    return UpdateOutcome.NOT_FOUND;
                }
                auditTrail.applicationChanged("changed", Long.toString(applicationId));
                return UpdateOutcome.CHANGED;
            });
        } catch (DataIntegrityViolationException codeAlreadyRegistered) {
            return UpdateOutcome.CODE_ALREADY_REGISTERED;
        }
    }

    /**
     * Excluir aplicacao e desativar, nao apagar.
     *
     * Apagar a linha deixaria conta de cliente e trilha de auditoria apontando para o nada,
     * e a trilha precisa continuar explicavel depois. A desativacao ja corta o acesso no
     * mesmo instante, porque a situacao e lida junto com a credencial.
     */
    @Transactional
    public boolean deactivate(long applicationId) {
        boolean deactivated = applications.deactivateById(applicationId, OffsetDateTime.now()) == 1;
        if (deactivated) {
            auditTrail.applicationChanged("deactivated", Long.toString(applicationId));
        }
        return deactivated;
    }

    public boolean exists(long applicationId) {
        return applications.existsById(applicationId);
    }
}
