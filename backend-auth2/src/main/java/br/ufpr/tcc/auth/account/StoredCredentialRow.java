package br.ufpr.tcc.auth.account;

/**
 * Projecao da consulta de login: dados da conta e um CNPJ por linha, nulo para conta sem
 * empresa. O diretorio agrupa as linhas. Publico para instanciacao pela consulta JPA.
 */
public record StoredCredentialRow(long id, String name, String email, String passwordHash,
        boolean emailConfirmed, boolean active, String cnpj) {
}
