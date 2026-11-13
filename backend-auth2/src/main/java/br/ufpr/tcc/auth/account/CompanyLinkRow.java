package br.ufpr.tcc.auth.account;

/**
 * Projecao do vinculo com ID da conta, ID da empresa e CNPJ. Publico para instanciacao pela
 * consulta JPA.
 */
public record CompanyLinkRow(long accountId, long companyId, String cnpj) {
}
