package br.ufpr.tcc.auth.authentication;

import java.util.List;
import java.util.Optional;

/**
 * Porta que a autenticacao exige do mundo para encontrar a credencial guardada.
 *
 * Ela e declarada aqui, por quem consome, e implementada pela funcionalidade de conta.
 * O escopo da aplicacao entra na assinatura e nao e opcional: repositorio que aceite
 * buscar so por e-mail deixa o filtro por aplicacao virar escolha de quem chama, e e
 * exatamente assim que nasce uma consulta que enxerga conta de outra aplicacao.
 */
public interface ClientCredentialDirectory {

    Optional<StoredClientCredentialView> findByApplicationAndEmail(long applicationId,
            String email);

    /** Projecao que a funcionalidade de conta preenche sem conhecer o tipo interno daqui. */
    record StoredClientCredentialView(long id, String name, String email, String passwordHash,
            boolean emailConfirmed, boolean active, List<String> companyCnpjs) {
    }
}
