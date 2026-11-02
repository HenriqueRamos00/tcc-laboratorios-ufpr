namespace Portal.Application.Quotes.Exceptions;

public sealed class QuoteAuthenticationRequiredException : Exception
{
    public QuoteAuthenticationRequiredException()
        : base("A consulta de orçamentos requer autenticação.")
    {
    }
}
