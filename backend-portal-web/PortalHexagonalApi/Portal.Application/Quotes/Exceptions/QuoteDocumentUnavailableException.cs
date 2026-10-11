namespace Portal.Application.Quotes.Exceptions;

/// <summary>
/// O documento do orcamento existe no indice mas nao pode ser lido. E falha distinta da
/// fonte de orcamentos nao responder: aqui a consulta funcionou e foi o arquivo que faltou,
/// e quem atende precisa saber a diferenca para procurar no lugar certo.
/// </summary>
public sealed class QuoteDocumentUnavailableException : Exception
{
    public QuoteDocumentUnavailableException(string message)
        : base(message)
    {
    }

    public QuoteDocumentUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
