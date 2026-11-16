namespace Portal.Domain.LabResults;

/// <summary>
/// Uma publicação vista pela série histórica: a data que ordena, o ID que identifica e
/// os valores indexados pelo codigo do item medido.
///
/// Os valores vem por codigo e nunca por indice. Indice obriga quem recebe a manter um
/// vetor de datas em sincronia com um vetor de valores, e esse invariante ja dessincronizou
/// dentro de uma massa escrita a mao.
/// </summary>
public sealed record SeriesReading(
    string AnalysisId,
    string Identification,
    DateTime ReportDate,
    IReadOnlyDictionary<string, MeasuredValue> ValuesByItemCode);
