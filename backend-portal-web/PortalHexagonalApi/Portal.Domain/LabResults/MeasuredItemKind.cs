namespace Portal.Domain.LabResults;

/// <summary>
/// Para que serve o item medido no laudo. O papel decide se a medicao entra no grafico de
/// evolucao, se fecha a tabela como soma, ou se pertence a outra tabela.
/// </summary>
public enum MeasuredItemKind
{
    DissolvedGas,
    GasTotalizer,
    PhysicalChemicalProperty
}
