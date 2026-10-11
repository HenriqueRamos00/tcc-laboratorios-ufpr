using System.Reflection;
using System.Text.Json;

using Portal.Application.LabResults.Exceptions;
using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Adapters.LabResults;

/// <summary>
/// Adaptador que le a amostra real das views, embutida no assembly como recurso. O nome diz
/// a tecnologia de proposito: ele nao imita SQL Server, nao tem conexao, nao tem transacao
/// e nao tem latencia, e chamar um adaptador assim de "fake SQL Server" promete uma
/// fidelidade que ele nao entrega.
///
/// Toda falha de leitura do recurso sai como <see cref="LabTestSourceUnavailableException"/>: quem
/// esta do outro lado da porta nao deve aprender a vocabulario de JSON nem de assembly.
/// </summary>
public sealed class EmbeddedSampleLabTestRepository : ILabTestRepository
{
    public const string SampleResourceName = "Portal.Adapters.LabResults.LabTestSample.jsonl";

    /// <summary>
    /// Laudos escritos a mao para que um mesmo equipamento tenha historico. O dado medido
    /// tem um laudo por equipamento, e com um ponto so nao existe serie: a tabela nao tem
    /// coluna de historico, o grafico nao tem linha e a paginacao do detalhe nunca vira de
    /// pagina. Fica em arquivo separado para que ninguem confunda numero escrito com numero
    /// medido, e o nome do recurso diz qual e qual.
    /// </summary>
    public const string DemonstrationSeriesResourceName =
        "Portal.Adapters.LabResults.DemonstrationSeries.jsonl";

    private static readonly Lazy<IReadOnlyList<SampleAnalysis>> SampleAnalyses =
        new(LoadSample, LazyThreadSafetyMode.ExecutionAndPublication);

    public Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfTenantAsync(
        string tenant,
        CancellationToken cancellationToken)
    {
        RequireTenant(tenant);

        IReadOnlyList<SampleAnalysis> ofTenant = [.. SampleAnalyses.Value
            .Where(analysis => BelongsToTenant(analysis, tenant))];

        return Task.FromResult(ofTenant);
    }

    public Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        string tenant,
        string equipmentKey,
        CancellationToken cancellationToken)
    {
        RequireTenant(tenant);

        IReadOnlyList<SampleAnalysis> found = [.. SampleAnalyses.Value
            .Where(analysis => BelongsToTenant(analysis, tenant))
            .Where(analysis => string.Equals(
                analysis.EquipmentKey,
                equipmentKey?.Trim(),
                StringComparison.Ordinal))];

        return Task.FromResult(found);
    }

    public Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        string tenant,
        string protocoloLactec,
        CancellationToken cancellationToken)
    {
        RequireTenant(tenant);

        SampleAnalysis? found = SampleAnalyses.Value
            .Where(analysis => BelongsToTenant(analysis, tenant))
            .FirstOrDefault(analysis =>
                string.Equals(analysis.ProtocoloLactec, protocoloLactec?.Trim(), StringComparison.Ordinal));

        return Task.FromResult(found);
    }

    /// <summary>
    /// Expoe a amostra para a suite poder afirmar sobre o dado real sem repetir o texto das
    /// views dentro do teste.
    /// </summary>
    public static IReadOnlyList<SampleAnalysis> ReadWholeSample()
    {
        return SampleAnalyses.Value;
    }

    /// <summary>
    /// So o dado medido, sem a serie escrita a mao. Existe porque teste que afirma sobre o
    /// que o laboratorio mediu nao pode passar a enxergar numero inventado quando alguem
    /// acrescenta um laudo de demonstracao.
    /// </summary>
    public static IReadOnlyList<SampleAnalysis> ReadMeasuredSample()
    {
        return BuildAnalyses(ReadResourceRows(SampleResourceName));
    }

    public static IReadOnlyList<SampleAnalysis> ReadDemonstrationSeries()
    {
        return BuildAnalyses(ReadResourceRows(DemonstrationSeriesResourceName));
    }

    public static IReadOnlyList<SampleAnalysis> BuildAnalyses(IEnumerable<LabTestDataViewRow> rows)
    {
        ArgumentNullException.ThrowIfNull(rows);

        return [.. rows
            .GroupBy(row => row.ProtocoloLactec, StringComparer.Ordinal)
            .Select(BuildOneAnalysis)];
    }

    private static void RequireTenant(string tenant)
    {
        if (string.IsNullOrWhiteSpace(tenant))
            throw new ArgumentException("Consulta sem inquilino.", nameof(tenant));
    }

    private static bool BelongsToTenant(SampleAnalysis analysis, string tenant)
    {
        return string.Equals(analysis.Company, tenant.Trim(), StringComparison.OrdinalIgnoreCase);
    }

    private static SampleAnalysis BuildOneAnalysis(IGrouping<string, LabTestDataViewRow> rowsOfProtocol)
    {
        LabTestDataViewRow first = rowsOfProtocol.First();

        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify(
            rowsOfProtocol.Select(row => new ResultBeforeRecognition(
                row.Name,
                MeasuredValue.InterpretResult(row.Resultado))));

        return new SampleAnalysis(
            first.ProtocoloLactec,
            first.Identificacao,
            first.NumSerie,
            first.EmpresaDoTransformador,
            first.DataEmissao,
            first.DataFinalAnalise,
            measurements);
    }

    private static IReadOnlyList<SampleAnalysis> LoadSample()
    {
        try
        {
            return BuildAnalyses([
                .. ReadResourceRows(SampleResourceName),
                .. ReadResourceRows(DemonstrationSeriesResourceName)]);
        }
        catch (Exception exception) when (exception is not LabTestSourceUnavailableException)
        {
            throw new LabTestSourceUnavailableException(
                "Falha ao ler a amostra embutida de ensaios.",
                exception);
        }
    }

    private static IEnumerable<LabTestDataViewRow> ReadResourceRows(string resourceName)
    {
        Assembly assembly = typeof(EmbeddedSampleLabTestRepository).Assembly;
        using Stream? resource = assembly.GetManifestResourceStream(resourceName);

        if (resource is null)
            throw new LabTestSourceUnavailableException($"Recurso embutido '{resourceName}' nao encontrado.");

        using StreamReader reader = new(resource);
        List<LabTestDataViewRow> rows = [];

        while (reader.ReadLine() is string line)
        {
            if (string.IsNullOrWhiteSpace(line)) continue;

            rows.Add(JsonSerializer.Deserialize<LabTestDataViewRow>(line)
                ?? throw new LabTestSourceUnavailableException($"Linha vazia no recurso '{resourceName}'."));
        }

        return rows;
    }
}
