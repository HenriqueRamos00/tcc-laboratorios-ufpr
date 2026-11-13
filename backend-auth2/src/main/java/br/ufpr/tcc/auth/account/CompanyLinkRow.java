package br.ufpr.tcc.auth.account;

/**
 * Linha crua de um vinculo entre conta e empresa, ja com o CNPJ da empresa.
 *
 * E publico porque a expressao de construtor da consulta o instancia por reflexao. Nenhum
 * pacote de fora o menciona.
 */
public record CompanyLinkRow(long accountId, long companyId, String cnpj) {
}
