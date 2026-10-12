namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// O mesmo item medido aparece duas vezes na mesma analise. A tabela do laudo tem uma linha
/// por item, entao a segunda ocorrencia ou sobrescreveria a primeira em silencio ou
/// duplicaria a linha; nos dois casos o laudo publicado deixa de corresponder a fonte.
/// </summary>
public sealed class RepeatedMeasuredItemException : Exception
{
    public RepeatedMeasuredItemException(string itemCode)
        : base($"Item medido '{itemCode}' aparece duas vezes na mesma analise.")
    {
        ItemCode = itemCode;
    }

    public string ItemCode { get; }
}
