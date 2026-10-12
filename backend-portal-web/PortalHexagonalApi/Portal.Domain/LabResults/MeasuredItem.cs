namespace Portal.Domain.LabResults;

/// <summary>
/// O que se sabe sobre um item medido independente de qualquer medicao. Nenhum destes campos
/// existe no AutoLAB: a view traz apenas o nome e o resultado. Formula, unidade, papel,
/// metodo e limite normativo nascem da lista de itens conhecidos, para que a invencao fique num
/// arquivo unico e visivel em vez de espalhada por inferencia sobre o nome.
/// </summary>
public sealed record MeasuredItem(
    string Code,
    string Label,
    string Formula,
    string Unit,
    MeasuredItemKind Role,
    bool IsCombustible,
    string TestMethod,
    string RegulatoryLimit);
