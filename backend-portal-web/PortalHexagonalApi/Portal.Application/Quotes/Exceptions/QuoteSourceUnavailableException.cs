namespace Portal.Application.Quotes.Exceptions;

/// <summary>
/// A fonte de orcamentos nao respondeu a consulta. Como a de ensaios, pertence ao contrato
/// da porta conduzida: hoje do outro lado ha um duble, amanha o Salesforce, e quem chama a
/// porta trata a mesma falha nos dois casos.
/// </summary>
public sealed class QuoteSourceUnavailableException : Exception
{
    public QuoteSourceUnavailableException(string message)
        : base(message)
    {
    }

    public QuoteSourceUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
