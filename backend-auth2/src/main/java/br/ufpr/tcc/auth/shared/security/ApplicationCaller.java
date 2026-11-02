package br.ufpr.tcc.auth.shared.security;

/**
 * Quem esta chamando, depois de a credencial ter sido reconhecida.
 *
 * Este e o unico lugar de onde o escopo da resposta pode sair. Identificador de aplicacao
 * que chegue no corpo, na consulta ou no caminho e pedido do chamador, nao identidade.
 */
public record ApplicationCaller(long applicationId, String applicationCode) {
}
