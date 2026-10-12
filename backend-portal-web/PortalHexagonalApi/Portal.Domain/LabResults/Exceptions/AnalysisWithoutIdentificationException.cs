namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// Analise sem o numero de identificacao do laudo. E o que a tela imprime no cabecalho e
/// na migalha de pao; sem ele o laudo aparece na lista sem nome nenhum.
/// </summary>
public sealed class AnalysisWithoutIdentificationException : Exception
{
    public AnalysisWithoutIdentificationException()
        : base("Analise sem identificacao nao pode ser exibida.")
    {
    }
}
