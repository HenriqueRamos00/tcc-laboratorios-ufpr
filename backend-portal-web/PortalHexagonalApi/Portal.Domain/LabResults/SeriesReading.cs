namespace Portal.Domain.LabResults;

/// <summary>
/// Uma analise vista pela serie historica: a data que ordena, o protocolo que identifica e
/// os valores indexados pelo codigo do item medido.
///
/// Os valores vem por codigo e nunca por indice. Indice obriga quem recebe a manter um
/// vetor de datas em sincronia com um vetor de valores, e esse invariante ja dessincronizou
/// dentro de uma massa escrita a mao.
/// </summary>
public sealed record SeriesReading(
    string ProtocoloLactec,
    string Identification,
    DateTime AnalysisEndDate,
    IReadOnlyDictionary<string, MeasuredValue> ValuesByItemCode);
