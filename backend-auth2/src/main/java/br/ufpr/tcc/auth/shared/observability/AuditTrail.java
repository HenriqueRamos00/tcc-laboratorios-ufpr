package br.ufpr.tcc.auth.shared.observability;

import br.ufpr.tcc.auth.shared.security.AdministratorCaller;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** A trilha de auditoria da regra 4 do Lactec. */
@Component
public class AuditTrail {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditTrail.class);

    private static final String ANONYMOUS_ACTOR = "sem-credencial";

    public void administratorChanged(String action, String administratorId) {
        LOGGER.atInfo()
                .addKeyValue("event", "administrator_" + action)
                .addKeyValue("actor", currentActor())
                .addKeyValue("administrator_id", administratorId)
                .log("Cadastro de administrador alterado");
    }

    /**
     * Tentativa de entrada de uma pessoa, com o desfecho.
     *
     * O endereco submetido nao entra na linha. Ele e dado pessoal, e registrar endereco em
     * tentativa recusada transformaria o log numa lista de quem tentou entrar no Lactec, que
     * e exatamente a informacao que a resposta HTTP se esforca para nao entregar. Quem precisa
     * ligar tentativas da mesma pessoa usa o identificador de correlacao e o endereco de
     * origem, que estao na mesma linha.
     */
    public void clientSignInAttempted(long applicationId, String outcome) {
        LOGGER.atInfo()
                .addKeyValue("event", "client_sign_in_attempted")
                .addKeyValue("application_id", Long.toString(applicationId))
                .addKeyValue("outcome", outcome)
                .log("Tentativa de entrada de cliente");
    }

    /** Bloqueio por excesso de tentativa, que a regra 3 cria e a regra 4 manda registrar. */
    public void clientLockedOut(long applicationId, long retryAfterSeconds) {
        LOGGER.atWarn()
                .addKeyValue("event", "client_locked_out")
                .addKeyValue("application_id", Long.toString(applicationId))
                .addKeyValue("retry_after_seconds", Long.toString(retryAfterSeconds))
                .log("Conta bloqueada por excesso de tentativa");
    }

    /** Criacao, alteracao e exclusao de conta de cliente. */
    public void clientAccountChanged(String action, String applicationId, String accountId) {
        LOGGER.atInfo()
                .addKeyValue("event", "client_account_" + action)
                .addKeyValue("actor", currentActor())
                .addKeyValue("application_id", applicationId)
                .addKeyValue("account_id", accountId)
                .log("Cadastro de cliente alterado");
    }

    /**
     * Cadastro, alteracao e exclusao de empresa.
     *
     * Empresa decide quem o cliente representa na aplicacao, entao ela entra na mesma lista
     * de cadastro que a conta: a regra 4 nomeia criacao, alteracao e exclusao sem abrir
     * excecao por tipo de registro.
     */
    public void companyChanged(String action, String applicationId, String companyId) {
        LOGGER.atInfo()
                .addKeyValue("event", "company_" + action)
                .addKeyValue("actor", currentActor())
                .addKeyValue("application_id", applicationId)
                .addKeyValue("company_id", companyId)
                .log("Cadastro de empresa alterado");
    }

    /** Confirmacao de endereco e troca de senha por recuperacao. */
    public void clientCredentialChanged(String action, String accountId) {
        LOGGER.atInfo()
                .addKeyValue("event", "client_credential_" + action)
                .addKeyValue("account_id", accountId)
                .log("Credencial de cliente alterada");
    }

    /** Entrada administrativa, com o desfecho, porque ela abre a porta de tudo o mais. */
    public void administrativeSignInAttempted(String outcome) {
        LOGGER.atInfo()
                .addKeyValue("event", "administrative_sign_in_attempted")
                .addKeyValue("outcome", outcome)
                .log("Tentativa de entrada administrativa");
    }

    /**
     * Bloqueio da entrada administrativa por excesso de tentativa.
     *
     * Evento proprio, e nao o de cliente: o contador guarda a entrada administrativa num
     * espaco de chave reservado, sem aplicacao, e reusar o evento de cliente faria a trilha
     * afirmar que uma conta da aplicacao zero foi bloqueada. Aplicacao zero nao existe.
     */
    public void administrativeLockedOut(long retryAfterSeconds) {
        LOGGER.atWarn()
                .addKeyValue("event", "administrative_locked_out")
                .addKeyValue("retry_after_seconds", Long.toString(retryAfterSeconds))
                .log("Entrada administrativa bloqueada por excesso de tentativa");
    }

    /** Cadastro e alteracao de aplicacao, que decide quem pode chamar o servico. */
    public void applicationChanged(String action, String applicationId) {
        LOGGER.atInfo()
                .addKeyValue("event", "application_" + action)
                .addKeyValue("actor", currentActor())
                .addKeyValue("application_id", applicationId)
                .log("Cadastro de aplicacao alterado");
    }

    /**
     * Emissao e revogacao de token de aplicacao.
     *
     * O identificador do token entra, o valor dele nunca. Quem emite recebe o segredo uma vez
     * so, na resposta, e nem o log nem o banco guardam outra copia.
     */
    public void applicationTokenChanged(String action, String applicationId, String tokenId) {
        LOGGER.atInfo()
                .addKeyValue("event", "application_token_" + action)
                .addKeyValue("actor", currentActor())
                .addKeyValue("application_id", applicationId)
                .addKeyValue("token_id", tokenId)
                .log("Token de aplicacao alterado");
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return ANONYMOUS_ACTOR;
        }
        return switch (authentication.getPrincipal()) {
            case AdministratorCaller administrator ->
                    "administrador:" + administrator.administratorId();
            case ApplicationCaller application -> "aplicacao:" + application.applicationId();
            default -> ANONYMOUS_ACTOR;
        };
    }
}
