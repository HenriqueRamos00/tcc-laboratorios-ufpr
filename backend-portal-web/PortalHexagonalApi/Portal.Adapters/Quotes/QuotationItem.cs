namespace Portal.Adapters.Quotes;

/// <summary>
/// Uma linha da tabela de servicos e ensaios do orcamento.
///
/// Hoje nenhuma fonte produz uma: a consulta do Salesforce nao pede <c>QuoteLineItem</c> e o
/// contrato do Spring nao traz itens, entao o documento e sempre montado com a colecao
/// vazia. O tipo existe mesmo assim porque a secao 2 e uma tabela e a tabela precisa saber
/// desenhar as duas situacoes; quando a porta comecar a carregar itens, ele migra para
/// <c>Portal.Domain/Quotes</c> e so o lugar dele muda.
/// </summary>
public sealed record QuotationItem(
    string Description,
    string Standard,
    decimal Quantity,
    decimal UnitPrice)
{
    public decimal TotalPrice => Quantity * UnitPrice;
}
