package br.ufpr.tcc.auth.authentication;

/**
 * Desfecho de uma tentativa de login de cliente.
 *
 * Sao tres e nao cinco de proposito. Senha errada, e-mail inexistente, conta de outra
 * aplicacao, conta nao confirmada e conta desativada colapsam todas em Rejected, e o
 * motivo real so existe na trilha de auditoria. Enquanto o tipo tiver um caso por motivo,
 * alguem acaba mapeando cada um para uma resposta diferente, e a diferenca entre eles e o
 * que entrega o cadastro a quem sonda.
 */
sealed interface ClientAuthentication {

    record Authenticated(ClientIdentity identity) implements ClientAuthentication {
    }

    record Rejected() implements ClientAuthentication {
    }

    record Locked(long retryAfterSeconds) implements ClientAuthentication {
    }

    static ClientAuthentication rejected() {
        return new Rejected();
    }
}
