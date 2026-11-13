package br.ufpr.tcc.auth.account;

/**
 * Linha crua da credencial guardada de um cliente, como a consulta a devolve.
 *
 * Ela existe porque a consulta precisa trazer, numa viagem so, a conta e o CNPJ das
 * empresas vinculadas, que vivem em tabelas diferentes. Quebrar isso em duas consultas poria
 * uma ida ao banco a mais no caminho mais quente do servico, que e o login.
 *
 * A conta volta repetida, uma linha por empresa, e o CNPJ e nulo quando ela nao representa
 * empresa nenhuma. Quem junta as linhas e traduz nulo para lista vazia e o diretorio, porque
 * a forma que o caso de uso consome e lista e nao campo que pode faltar.
 *
 * E publico porque a expressao de construtor da consulta o instancia por reflexao. Nenhum
 * pacote de fora o menciona.
 */
public record StoredCredentialRow(long id, String name, String email, String passwordHash,
        boolean emailConfirmed, boolean active, String cnpj) {
}
