namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// Analise sem empresa. O nome da empresa nao decide quem ve o laudo (isso e o CNPJ do
/// cliente da solicitacao), mas e ele que a tela mostra como dono do equipamento: um laudo
/// sem dono declarado nao e montado.
/// </summary>
public sealed class AnalysisWithoutCompanyException : Exception
{
    public AnalysisWithoutCompanyException()
        : base("Analise sem empresa nao pode ser atribuida a um cliente.")
    {
    }
}
