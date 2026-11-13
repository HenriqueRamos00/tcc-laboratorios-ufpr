package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.authentication.ClientCredentialDirectory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Le a credencial guardada de um cliente, sempre dentro de uma aplicacao.
 *
 * Nao existe aqui metodo que busque so por e-mail. A assinatura exige o escopo, e com isso
 * consultar conta de outra aplicacao deixa de ser descuido possivel e passa a ser codigo
 * que nao compila.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ClientAccountDirectory implements ClientCredentialDirectory {

    private final ClientAccountRepository accounts;

    /**
     * Os CNPJ saem em ordem, para que a resposta do login nao mude de uma chamada para a
     * outra conforme o plano que o banco escolheu.
     */
    @Override
    public Optional<StoredClientCredentialView> findByApplicationAndEmail(
            long applicationId, String email) {
        List<StoredCredentialRow> rows = accounts.findCredentialWithinApplication(
                applicationId, ClientAccountRegistry.withoutSurroundingSpace(email));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        StoredCredentialRow account = rows.get(0);
        List<String> companyCnpjs = rows.stream().map(StoredCredentialRow::cnpj)
                .filter(Objects::nonNull).sorted().toList();
        return Optional.of(new StoredClientCredentialView(account.id(), account.name(),
                account.email(), account.passwordHash(), account.emailConfirmed(),
                account.active(), companyCnpjs));
    }
}
