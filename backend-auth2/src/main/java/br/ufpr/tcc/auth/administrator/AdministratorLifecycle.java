package br.ufpr.tcc.auth.administrator;

import br.ufpr.tcc.auth.credential.model.PasswordPolicy;
import br.ufpr.tcc.auth.shared.identifier.IdentifierFactory;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de administrador depois da instalacao: criar, atualizar e excluir.
 *
 * A instalacao do primeiro administrador nao mora aqui, e sim em AdministratorAccess, porque
 * ela e a unica que acontece sem ninguem autenticado e carrega a regra do segredo de
 * ambiente. Daqui para frente todo cadastro e feito por um administrador que ja entrou.
 *
 * A regra que atravessa as tres operacoes e uma so: o servico nunca pode ficar sem nenhum
 * administrador ativo. Desativar o ultimo tranca todo mundo fora de forma definitiva, porque
 * a rota de instalacao conta linha na tabela e a linha desativada continua contando.
 */
@Service
public class AdministratorLifecycle {

    private final AdministratorRegistry registry;
    private final AdministrativeSessionDirectory sessions;
    private final PasswordEncoder passwordEncoder;
    private final IdentifierFactory identifiers;
    private final AuditTrail auditTrail;

    AdministratorLifecycle(AdministratorRegistry registry, AdministrativeSessionDirectory sessions,
            PasswordEncoder passwordEncoder, IdentifierFactory identifiers,
            AuditTrail auditTrail) {
        this.registry = registry;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.identifiers = identifiers;
        this.auditTrail = auditTrail;
    }

    /**
     * Desfecho do cadastro. O administrador criado viaja junto porque quem cadastra precisa
     * do identificador para enderecar as rotas seguintes.
     */
    public record Registration(Outcome outcome, AdministratorSummary administrator) {

        public enum Outcome { CREATED, EMAIL_ALREADY_REGISTERED, PASSWORD_REJECTED }
    }

    /**
     * A consulta anterior nao substitui a restricao unica do banco. Duas requisicoes
     * simultaneas com o mesmo endereco passam as duas pela consulta, e quem recusa a segunda
     * e a restricao; sem traduzir essa falha o desfecho seria erro de servidor no lugar do
     * conflito que a matriz manda.
     */
    @Transactional
    public Registration register(String name, String email, String password) {
        if (!PasswordPolicy.violationsOf(password).isEmpty()) {
            return new Registration(Registration.Outcome.PASSWORD_REJECTED, null);
        }
        if (registry.findByEmail(email).isPresent()) {
            return new Registration(Registration.Outcome.EMAIL_ALREADY_REGISTERED, null);
        }
        long identifier = identifiers.nextIdentifier();
        try {
            registry.insert(identifier, name, email, passwordEncoder.encode(password));
        } catch (DuplicateKeyException addressAlreadyTaken) {
            return new Registration(Registration.Outcome.EMAIL_ALREADY_REGISTERED, null);
        }
        auditTrail.administratorChanged("created", Long.toString(identifier));
        return new Registration(Registration.Outcome.CREATED,
                new AdministratorSummary(Long.toString(identifier), name, email, true));
    }

    /** Desfecho da atualizacao, com o administrador como ele ficou. */
    public record Change(Outcome outcome, AdministratorSummary administrator) {

        public enum Outcome {
            CHANGED, NOT_FOUND, EMAIL_ALREADY_REGISTERED, PASSWORD_REJECTED,
            LAST_ACTIVE_ADMINISTRATOR
        }
    }

    /**
     * Senha ausente no pedido significa senha mantida. Ela nao e obrigatoria aqui porque
     * atualizar nome ou situacao e uma operacao propria, e exigir a senha para isso obrigaria
     * quem administra a escolher uma nova a cada ajuste de cadastro.
     *
     * Desativar encerra as sessoes abertas, e nao apenas deixa de resolver o token enquanto a
     * situacao estiver desligada. A consulta de sessao exige administrador ativo, entao
     * desativado ele nao e atendido, mas a linha da sessao continua valida: medido aqui, sem
     * encerrar, reativar devolvia o acesso a quem estava com o token no dia do afastamento,
     * que e justamente o motivo pelo qual se desativa alguem.
     *
     * O hash da senha nova e calculado antes da trava de tabela, e a ordem aqui nao e
     * estetica. A contagem de administrador ativo toma LOCK TABLE IN EXCLUSIVE MODE, e com o
     * calculo depois dela a transacao ficava segurando a tabela inteira pelos 32 ms medidos
     * de uma verificacao de senha: todo DML sobre administrador esperava um hash terminar,
     * por uma razao que nada tem a ver com a trava.
     */
    @Transactional
    public Change update(String identifier, String name, String email, boolean active,
            String password) {
        Optional<Long> parsed = AdministratorRegistry.identifierOf(identifier);
        if (parsed.isEmpty() || registry.findById(parsed.get()).isEmpty()) {
            return new Change(Change.Outcome.NOT_FOUND, null);
        }
        long id = parsed.get();
        if (password != null && !PasswordPolicy.violationsOf(password).isEmpty()) {
            return new Change(Change.Outcome.PASSWORD_REJECTED, null);
        }
        String newPasswordHash = password == null ? null : passwordEncoder.encode(password);
        if (registry.emailBelongsToAnother(email, id)) {
            return new Change(Change.Outcome.EMAIL_ALREADY_REGISTERED, null);
        }
        if (!active && registry.activeCountOtherThanUnderLock(id) == 0L) {
            return new Change(Change.Outcome.LAST_ACTIVE_ADMINISTRATOR, null);
        }
        try {
            registry.update(id, name, email, active);
        } catch (DuplicateKeyException addressAlreadyTaken) {
            return new Change(Change.Outcome.EMAIL_ALREADY_REGISTERED, null);
        }
        if (newPasswordHash != null) {
            registry.updatePasswordHash(id, newPasswordHash);
        }
        if (password != null || !active) {
            sessions.revokeAllOf(id);
        }
        auditTrail.administratorChanged("changed", Long.toString(id));
        return new Change(Change.Outcome.CHANGED,
                new AdministratorSummary(Long.toString(id), name, email, active));
    }

    /** Desfecho da exclusao. */
    public enum Removal { REMOVED, NOT_FOUND, LAST_ACTIVE_ADMINISTRATOR }

    /**
     * Excluir administrador desativado e permitido, porque ele nao e o que sustenta o
     * servico. Excluir o ultimo ativo nao e, pelo mesmo motivo que desativa-lo nao e.
     */
    @Transactional
    public Removal delete(String identifier) {
        Optional<AdministratorRegistry.AdministratorView> stored =
                AdministratorRegistry.identifierOf(identifier).flatMap(registry::findById);
        if (stored.isEmpty()) {
            return Removal.NOT_FOUND;
        }
        long id = Long.parseLong(stored.get().id());
        if (stored.get().active() && registry.activeCountOtherThanUnderLock(id) == 0L) {
            return Removal.LAST_ACTIVE_ADMINISTRATOR;
        }
        sessions.deleteAllOf(id);
        registry.delete(id);
        auditTrail.administratorChanged("removed", Long.toString(id));
        return Removal.REMOVED;
    }
}
