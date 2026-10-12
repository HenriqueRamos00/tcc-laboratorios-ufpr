namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// Analise que chegou sem nenhuma medicao. Um laudo sem medicao nao e um laudo vazio: e um
/// erro de leitura da fonte, porque a view entrega uma linha por item medido e um protocolo
/// sem linha nenhuma nao existe.
/// </summary>
public sealed class AnalysisWithoutMeasurementsException : Exception
{
    public AnalysisWithoutMeasurementsException()
        : base("Analise sem nenhuma medicao nao e analise.")
    {
    }
}
