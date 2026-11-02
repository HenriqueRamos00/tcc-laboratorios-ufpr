namespace Portal.Application.Quotes.Exceptions;

public sealed class QuoteAccessForbiddenException : Exception
{
    public QuoteAccessForbiddenException()
        : base("O acesso ao orçamento foi proibido.")
    {
    }
}
