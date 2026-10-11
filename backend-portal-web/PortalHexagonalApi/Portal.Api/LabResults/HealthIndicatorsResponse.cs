using Portal.Domain.LabResults;

namespace Portal.Api.LabResults;

/// <summary>
/// O corpo que a tela de indicadores de saude ja sabe ler, campo por campo igual a
/// <c>frontend/src/app/model/health.ts</c>. E vocabulario de um ator so, entao mora na
/// borda e nunca no dominio.
///
/// Os nomes de propriedade em C# falam de analise, porque a coluna de data de coleta nao
/// existe no AutoLAB e nenhum identificador novo pode prometer uma. Os nomes no fio
/// continuam <c>coletas</c> e <c>datasDeColeta</c> porque sao o contrato que a tela ja
/// consome, e renomear o fio nesta fatia quebraria a tela sem entregar nada em troca. A
/// divergencia fica confinada ao atributo de serializacao, onde da para ver.
/// </summary>
public sealed record HealthIndicatorsResponse
{
    public required string EquipmentId { get; init; }

    public required string Tag { get; init; }

    public SelectedAnalysisResponse? SelectedAnalysis { get; init; }

    public required PhysicalChemicalBlockResponse PhysicalChemical { get; init; }

    public required DissolvedGasBlockResponse DissolvedGases { get; init; }

    /// <summary>
    /// Nulo nesta fatia. O nucleo normativo de Duval ainda nao foi migrado, e preencher um
    /// pentagono ou uma conformidade sem calcular faria o portal afirmar um diagnostico que
    /// ninguem produziu.
    /// </summary>
    public object? Diagnosis { get; init; }

    public required IReadOnlyList<UnreadableResultResponse> UnreadableResults { get; init; }

    public required IReadOnlyList<TotalizerCheckResponse> TotalizerChecks { get; init; }
}

public sealed record SelectedAnalysisResponse
{
    public required string Id { get; init; }

    public required string Date { get; init; }

    public required string Label { get; init; }
}

public sealed record TestReportResponse
{
    public required string Title { get; init; }

    public required IReadOnlyList<string> Paragraphs { get; init; }
}

public sealed record AnalysisColumnResponse
{
    public required string Date { get; init; }

    public required bool IsCurrentResult { get; init; }
}

public sealed record SeriesReadingResponse
{
    public required string Date { get; init; }

    public required IReadOnlyDictionary<string, decimal?> Values { get; init; }
}

public sealed record PhysicalChemicalTestResponse
{
    public required string Key { get; init; }

    public required string Name { get; init; }

    public required string Method { get; init; }

    public required string Limit { get; init; }

    public required IReadOnlyList<object?> ValuesByAnalysis { get; init; }

    /// <summary>
    /// Nula nesta fatia: a classificacao e regra de negocio e sai do nucleo normativo, que
    /// ainda nao migrou. Emitir "normal" sem avaliar pintaria o medidor de verde.
    /// </summary>
    public string? Classification { get; init; }
}

public sealed record DissolvedGasResponse
{
    public required string Key { get; init; }

    public required string Name { get; init; }

    public required string Formula { get; init; }

    public required IReadOnlyList<object?> Results { get; init; }
}

public sealed record PhysicalChemicalBlockResponse
{
    public string? Conformity { get; init; }

    public string? Verdict { get; init; }

    public required string NominalVoltage { get; init; }

    public required IReadOnlyList<AnalysisColumnResponse> Columns { get; init; }

    public required IReadOnlyList<SeriesReadingResponse> Analyses { get; init; }

    public required IReadOnlyList<PhysicalChemicalTestResponse> Tests { get; init; }

    public required TestReportResponse Report { get; init; }
}

public sealed record DissolvedGasBlockResponse
{
    public string? Conformity { get; init; }

    public string? Verdict { get; init; }

    public required IReadOnlyList<string> AnalysisDates { get; init; }

    public required IReadOnlyList<SeriesReadingResponse> Analyses { get; init; }

    public required IReadOnlyList<DissolvedGasResponse> Gases { get; init; }

    public required TestReportResponse Report { get; init; }
}

public sealed record UnreadableResultResponse
{
    public required string Key { get; init; }

    public required string Name { get; init; }

    public required string OriginalText { get; init; }

    public required string ProtocoloLactec { get; init; }
}

/// <summary>
/// <see cref="Resultado"/> viaja como texto, e nao como booleano nem como numero de enum.
/// Booleano nao tem onde por o terceiro desfecho, e numero obriga a tela a conhecer a ordem
/// de declaracao do enum, que muda sem ninguem perceber ao inserir um caso no meio.
/// </summary>
public sealed record TotalizerCheckResponse
{
    public required string Key { get; init; }

    public required decimal PublishedValue { get; init; }

    public required decimal MeasuredSum { get; init; }

    public required int GasesWithoutTest { get; init; }

    public required TotalizerCheckResult Result { get; init; }
}
