package br.ufpr.tcc.auth.company;

import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastro de empresa, feito pelo administrador.
 *
 * A empresa nao pertence a aplicacao nenhuma, entao nao existe escopo nas assinaturas: o
 * alcance e cobrado uma vez, na cadeia de seguranca, que so deixa chegar aqui quem
 * apresentou credencial administrativa.
 *
 * Cadastro, alteracao e exclusao abrem a transacao pelo modelo e nao pela anotacao, porque
 * as tres disputam uma restricao do banco e precisam traduzir a recusa dela para conflito:
 * as duas primeiras a restricao unica do CNPJ, e a exclusao a chave estrangeira do vinculo
 * com conta. A recusa chega como excecao de integridade e o provedor ja marcou a transacao
 * para desfazer, entao tratar isso dentro dela faria o commit falhar com erro de servidor
 * onde o contrato promete 409.
 */
@Service
public class CompanyRegistry {

    public record CompanyView(String id, String name, String cnpj) {
    }

    private final CompanyRepository companies;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    CompanyRegistry(CompanyRepository companies, AuditTrail auditTrail,
            TransactionTemplate transactions) {
        this.companies = companies;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
    }

    private static CompanyView toView(Company company) {
        return new CompanyView(Long.toString(company.id()), company.name(), company.cnpj());
    }

    public List<CompanyView> list() {
        return companies.findByOrderByCreatedAtDesc().stream()
                .map(CompanyRegistry::toView).toList();
    }

    public Optional<CompanyView> find(long companyId) {
        return companies.findById(companyId).map(CompanyRegistry::toView);
    }

    /** Desfecho do cadastro, com a empresa criada quando houve criacao. */
    public record Registration(Outcome outcome, CompanyView company) {

        public enum Outcome { CREATED, CNPJ_ALREADY_REGISTERED }
    }

    /** Desfecho da alteracao, com o conflito de CNPJ separado de nao encontrado. */
    public enum UpdateOutcome { CHANGED, NOT_FOUND, CNPJ_ALREADY_REGISTERED }

    /** Desfecho da exclusao, com a empresa ainda vinculada separada de nao encontrada. */
    public enum Removal { REMOVED, NOT_FOUND, STILL_LINKED }

    /**
     * A consulta de CNPJ repetido nao substitui a restricao unica do banco: entre consultar
     * e gravar existe janela, e dois cadastros simultaneos do mesmo CNPJ passam os dois pela
     * consulta. Quem recusa o segundo e a restricao.
     *
     * A consulta vive dentro da transacao da gravacao, e nao no controlador, por dois
     * motivos. O controlador nao decide regra de negocio, e a consulta fora daquela transacao
     * abre a janela ainda mais.
     */
    public Registration register(String name, String cnpj) {
        try {
            return transactions.execute(status -> {
                if (companies.countByCnpj(cnpj) > 0L) {
                    return new Registration(Registration.Outcome.CNPJ_ALREADY_REGISTERED, null);
                }
                Company registered = companies.saveAndFlush(Company.registered(name, cnpj));
                auditTrail.companyChanged("registered", Long.toString(registered.id()));
                return new Registration(Registration.Outcome.CREATED, toView(registered));
            });
        } catch (DataIntegrityViolationException cnpjAlreadyRegistered) {
            return new Registration(Registration.Outcome.CNPJ_ALREADY_REGISTERED, null);
        }
    }

    /**
     * Mover o CNPJ para um que ja existe e conflito, e nao erro de servidor. A alteracao
     * disputa a mesma restricao unica que o cadastro, entao traduz a recusa do mesmo jeito.
     */
    public UpdateOutcome update(long companyId, String name, String cnpj) {
        try {
            return transactions.execute(status -> {
                if (companies.updateById(companyId, name, cnpj, OffsetDateTime.now()) != 1) {
                    return UpdateOutcome.NOT_FOUND;
                }
                auditTrail.companyChanged("changed", Long.toString(companyId));
                return UpdateOutcome.CHANGED;
            });
        } catch (DataIntegrityViolationException cnpjAlreadyRegistered) {
            return UpdateOutcome.CNPJ_ALREADY_REGISTERED;
        }
    }

    /**
     * Empresa que alguma conta ainda representa nao e removida: quem exclui precisa antes
     * desfazer os vinculos, na alteracao de cada conta.
     *
     * Nao existe consulta de vinculo antes do comando, de proposito. Ela abriria janela
     * entre consultar e apagar, e quem fecha essa janela e a chave estrangeira; entao a
     * recusa dela e a unica fonte, e vira conflito aqui.
     */
    public Removal delete(long companyId) {
        try {
            return transactions.execute(status -> {
                if (companies.deleteWithId(companyId) != 1) {
                    return Removal.NOT_FOUND;
                }
                auditTrail.companyChanged("removed", Long.toString(companyId));
                return Removal.REMOVED;
            });
        } catch (DataIntegrityViolationException stillRepresentedBySomeAccount) {
            return Removal.STILL_LINKED;
        }
    }
}
