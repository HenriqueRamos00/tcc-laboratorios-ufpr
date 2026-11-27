package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.shared.identifier.Identifiers;
import br.ufpr.tcc.auth.shared.observability.AuditTrail;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Altera nome e situacao da conta e substitui a lista inteira de empresas.
 *
 * Rota: PUT /api/users/{id}, credencial administrativa. E-mail, perfil e aplicacao nao sao
 * alteraveis por aqui. Empresa inexistente desfaz toda a operacao.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class UpdateClientAccountUseCase {

    /** Desfecho da alteracao, com a conta como ficou quando houve alteracao. */
    public record Result(Outcome outcome, AccountSummary account) {

        public enum Outcome { CHANGED, NOT_FOUND, COMPANY_NOT_FOUND }

        static Result refused(Outcome outcome) {
            return new Result(outcome, null);
        }
    }

    private final ClientAccountStore store;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;

    /**
     * A resposta usa os valores pedidos porque o UPDATE direto nao atualiza a entidade em
     * memoria.
     */
    public Result execute(Optional<Long> applicationScope, String accountId, String name,
            boolean active, List<Long> companyIds) {
        Optional<Long> parsed = Identifiers.parse(accountId);
        if (parsed.isEmpty()) {
            return Result.refused(Result.Outcome.NOT_FOUND);
        }
        long id = parsed.get();
        Set<Long> companies = new LinkedHashSet<>(companyIds);
        return transactions.execute(status -> {
            Optional<ClientAccountStore.AccountView> stored =
                    store.findWithinScope(applicationScope, id);
            if (stored.isEmpty()) {
                return Result.refused(Result.Outcome.NOT_FOUND);
            }
            ClientAccountStore.AccountView found = stored.get();
            store.update(applicationScope, id, name, active);
            if (!store.replaceCompanies(id, companies)) {
                status.setRollbackOnly();
                return Result.refused(Result.Outcome.COMPANY_NOT_FOUND);
            }
            auditTrail.clientAccountChanged("changed", found.serviceId(), found.id());
            ClientAccountStore.CompanyLinks linked = store.companiesOf(id);
            return new Result(Result.Outcome.CHANGED,
                    AccountSummary.of(new ClientAccountStore.AccountView(found.id(), name,
                            found.email(), found.role(), active, found.serviceId(),
                            found.emailConfirmed(), linked.companyIds(),
                            linked.companyCnpjs())));
        });
    }
}
