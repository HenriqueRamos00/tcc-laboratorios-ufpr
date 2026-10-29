package br.ufpr.tcc.auth.account;

/**
 * Linha crua da credencial guardada de um cliente, como a consulta a devolve.
 *
 * Ela existe porque a consulta precisa trazer, numa viagem so, a conta e o CNPJ da empresa
 * vinculada, que vivem em tabelas diferentes. Quebrar isso em duas consultas poria uma ida
 * ao banco a mais no caminho mais quente do servico, que e o login.
 *
 * O CNPJ e nulo quando a conta nao representa empresa nenhuma, e o vinculo e opcional. Quem
 * traduz nulo para lista vazia e o diretorio, porque a forma que o caso de uso consome e
 * lista e nao campo que pode faltar.
 *
 * E publico porque a expressao de construtor da consulta o instancia por reflexao. Nenhum
 * pacote de fora o menciona.
 */
public record StoredCredentialRow(long id, String name, String email, String passwordHash,
        boolean emailConfirmed, boolean active, String cnpj) {
}
