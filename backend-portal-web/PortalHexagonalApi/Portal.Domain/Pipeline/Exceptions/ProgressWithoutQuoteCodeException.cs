namespace Portal.Domain.Pipeline.Exceptions;

/// <summary>
/// Tentou-se afirmar uma etapa de laboratorio sem dizer de qual orcamento ela e.
///
/// Falhar aqui e deliberado. Uma etapa sem codigo nao casa com orcamento nenhum, entao ou
/// ela desapareceria em silencio, ou, se alguem casasse por vazio, apareceria no orcamento
/// errado do cliente errado. O adaptador que le a fonte ja descarta linha sem
/// <c>OrcamentoCRM</c>; esta excecao e a garantia de que um adaptador novo nao reintroduza
/// o caso sem ninguem perceber.
/// </summary>
public sealed class ProgressWithoutQuoteCodeException : Exception
{
    public ProgressWithoutQuoteCodeException()
        : base("Etapa de laboratorio sem codigo de orcamento.")
    {
    }
}
