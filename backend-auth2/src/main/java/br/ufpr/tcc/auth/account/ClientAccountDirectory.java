package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.authentication.ClientCredentialDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Le a credencial guardada de um cliente, sempre dentro de uma aplicacao.
 *
 * Nao existe aqui metodo que busque so por e-mail. A assinatura exige o escopo, e com isso
 * consultar conta de outra aplicacao deixa de ser descuido possivel e passa a ser codigo
 * que nao compila.
 */
@Component
class ClientAccountDirectory implements ClientCredentialDirectory {

    private final ClientAccountRepository accounts;

    ClientAccountDirectory(ClientAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<StoredClientCredentialView> findByApplicationAndEmail(
            long applicationId, String email) {
        return accounts
                .findCredentialWithinApplication(applicationId,
                        ClientAccountRegistry.withoutSurroundingSpace(email))
                .map(row -> new StoredClientCredentialView(row.id(), row.name(), row.email(),
                        row.passwordHash(), row.emailConfirmed(), row.active(),
                        row.cnpj() == null ? List.of() : List.of(row.cnpj())));
    }
}
