package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import java.util.List;

/** O que as rotas de cadastro, leitura e alteracao devolvem sobre uma conta. */
public record AccountSummary(String id, String name, String email, String role, boolean active,
        String serviceId, boolean emailConfirmed, List<String> companyIds,
        Authorizations authorizations) {

    /** As empresas que a conta representa, na forma que o consumidor do login ja conhece. */
    public record Authorizations(List<String> companyCnpjs) {
    }

    static AccountSummary of(ClientAccountStore.AccountView view) {
        return new AccountSummary(view.id(), view.name(), view.email(), view.role(), view.active(),
                view.serviceId(), view.emailConfirmed(), view.companyIds(),
                new Authorizations(view.companyCnpjs()));
    }
}
