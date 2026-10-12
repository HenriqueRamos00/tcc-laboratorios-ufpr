namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// Buraco no meio da lista de medicoes. Aceitar o nulo adiaria a falha para o primeiro
/// laco que percorresse a lista, longe de quem montou a analise.
/// </summary>
public sealed class NullMeasurementException : Exception
{
    public NullMeasurementException()
        : base("Medicao nula na lista de medicoes.")
    {
    }
}
