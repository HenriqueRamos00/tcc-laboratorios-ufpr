package br.ufpr.tcc.auth.authentication;

import java.util.List;

/**
 * A credencial como ela esta guardada, do jeito que a autenticacao precisa dela.
 *
 * As bandeiras vem juntas com a forma guardada da senha, numa leitura so, para que o
 * caminho de decisao nao tenha uma consulta por condicao: consulta a mais e tempo a mais,
 * e tempo a mais e o que denuncia qual condicao recusou.
 */
record StoredClientCredential(long id, String name, String email, String passwordHash,
        boolean emailConfirmed, boolean active, List<String> companyCnpjs) {
}
