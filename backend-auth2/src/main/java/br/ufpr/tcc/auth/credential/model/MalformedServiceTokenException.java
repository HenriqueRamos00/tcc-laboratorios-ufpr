package br.ufpr.tcc.auth.credential.model;

/**
 * Token de aplicacao apresentado fora do formato de contrato.
 *
 * Esta falha e de autenticacao, nao de infraestrutura: quem apresenta valor malformado
 * recebe a mesma recusa de quem apresenta token desconhecido. Ela tem tipo proprio
 * justamente para nao ser IllegalArgumentException, que o tratador padrao levaria para 400
 * ou 500 e entregaria ao chamador a diferenca entre formato errado e token inexistente.
 *
 * A mensagem nao carrega o valor recusado, porque mensagem de excecao vai para o log, e
 * token em log e proibido.
 */
public final class MalformedServiceTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedServiceTokenException() {
        super("Token de aplicacao fora do formato esperado");
    }
}
