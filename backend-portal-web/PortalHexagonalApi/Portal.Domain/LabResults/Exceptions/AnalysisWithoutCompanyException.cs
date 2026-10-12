namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// Analise sem empresa. A empresa e o inquilino: sem ela a analise nao pertence a cliente
/// nenhum, e toda consulta do portal filtra por inquilino antes de qualquer outra coisa.
/// Deixar passar significaria um laudo visivel para todo mundo.
/// </summary>
public sealed class AnalysisWithoutCompanyException : Exception
{
    public AnalysisWithoutCompanyException()
        : base("Analise sem empresa nao pode ser filtrada por inquilino.")
    {
    }
}
