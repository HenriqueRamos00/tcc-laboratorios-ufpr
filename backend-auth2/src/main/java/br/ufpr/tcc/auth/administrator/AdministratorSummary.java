package br.ufpr.tcc.auth.administrator;

/**
 * Administrador como ele sai na resposta: identificacao, contato e situacao.
 *
 * Nenhum campo de credencial entra aqui. O tipo e o que a camada web devolve, entao um
 * campo de senha ou de forma guardada acrescentado neste record apareceria no corpo de
 * todas as rotas de administrador de uma vez.
 */
public record AdministratorSummary(String id, String name, String email, boolean active) {
}
