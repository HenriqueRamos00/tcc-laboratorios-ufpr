package br.ufpr.tcc.auth.company;

import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cadastro de empresa dentro de uma aplicacao.
 *
 * O escopo entra em toda assinatura e nao e opcional. Repositorio que aceite buscar empresa
 * so por identificador deixa o filtro por aplicacao virar escolha de quem chama, e e assim
 * que nasce uma consulta que enxerga cadastro alheio.
 *
 * Cadastro e alteracao abrem a transacao pelo modelo e nao pela anotacao, porque as duas
 * disputam a restricao unica do CNPJ e precisam traduzir a recusa dela para conflito. A
 * recusa chega como excecao de integridade e o provedor ja marcou a transacao para desfazer,
 * entao tratar isso dentro dela faria o commit falhar com erro de servidor onde o contrato
 * promete 409.
 */
@Service
public class CompanyRegistry {

    public record CompanyView(String id, String name, String cnpj, String serviceId) {
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
        return new CompanyView(Long.toString(company.id()), company.name(), company.cnpj(),
                Long.toString(company.applicationId()));
    }

    public List<CompanyView> list(long applicationId) {
        return companies.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(CompanyRegistry::toView).toList();
    }

    public Optional<CompanyView> find(long applicationId, long companyId) {
        return companies.findByIdAndApplicationId(companyId, applicationId)
                .map(CompanyRegistry::toView);
    }

    /** Desfecho do cadastro, com a empresa como ela ficou quando houve cadastro. */
    public record Registration(Outcome outcome, CompanyView company) {

        public enum Outcome { CREATED, CNPJ_ALREADY_REGISTERED }
    }

    /** Desfecho da alteracao, com o conflito de CNPJ separado de nao encontrado. */
    public enum UpdateOutcome { CHANGED, NOT_FOUND, CNPJ_ALREADY_REGISTERED }

    /**
     * A consulta de CNPJ repetido nao substitui a restricao unica do banco: entre consultar
     * e gravar existe janela, e dois cadastros simultaneos do mesmo CNPJ passam os dois pela
     * consulta. Quem recusa o segundo e a restricao.
     *
     * A consulta vive dentro da transacao da gravacao, e nao no controlador, por dois
     * motivos. O controlador nao decide regra de negocio, e a consulta fora daquela transacao
     * abre a janela ainda mais.
     */
    public Registration register(long applicationId, String name, String cnpj) {
        try {
            return transactions.execute(status -> {
                if (companies.countByApplicationIdAndCnpj(applicationId, cnpj) > 0L) {
                    return new Registration(Registration.Outcome.CNPJ_ALREADY_REGISTERED, null);
                }
                Company registered = companies
                        .saveAndFlush(Company.registeredIn(applicationId, name, cnpj));
                auditTrail.companyChanged("registered", Long.toString(applicationId),
                        Long.toString(registered.id()));
                return new Registration(Registration.Outcome.CREATED, toView(registered));
            });
        } catch (DataIntegrityViolationException cnpjAlreadyRegistered) {
            return new Registration(Registration.Outcome.CNPJ_ALREADY_REGISTERED, null);
        }
    }

    /**
     * Mover o CNPJ para um que ja existe na mesma aplicacao e conflito, nao erro de
     * servidor. Aqui nao havia nem a consulta previa que o cadastro tem: a alteracao chegava
     * ao banco, a restricao unica recusava, e ninguem traduzia a recusa. Medido antes desta
     * traducao, com uma requisicao sequencial: ORA-00001 subia e a rota respondia erro de
     * servidor, que no consumidor vira fonte indisponivel.
     */
    public UpdateOutcome update(long applicationId, long companyId, String name, String cnpj) {
        try {
            return transactions.execute(status -> {
                if (companies.findByIdAndApplicationId(companyId, applicationId).isEmpty()) {
                    return UpdateOutcome.NOT_FOUND;
                }
                companies.updateWithinApplication(companyId, applicationId, name, cnpj,
                        OffsetDateTime.now());
                auditTrail.companyChanged("changed", Long.toString(applicationId),
                        Long.toString(companyId));
                return UpdateOutcome.CHANGED;
            });
        } catch (DataIntegrityViolationException cnpjAlreadyRegistered) {
            return UpdateOutcome.CNPJ_ALREADY_REGISTERED;
        }
    }

    /**
     * Remove a empresa dentro da aplicacao dela. Conta vinculada impede a remocao pela
     * chave estrangeira, e isso e o comportamento desejado: apagar a empresa deixaria a
     * conta apontando para o nada.
     */
    @Transactional
    public boolean delete(long applicationId, long companyId) {
        boolean removed = companies.deleteWithinApplication(companyId, applicationId) == 1;
        if (removed) {
            auditTrail.companyChanged("removed", Long.toString(applicationId),
                    Long.toString(companyId));
        }
        return removed;
    }
}
