using Portal.Application.LabResults.Exceptions;
using Portal.Domain.LabResults.Exceptions;
using Portal.Adapters.LabResults;
using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class EmbeddedSampleLabTestRepositoryTests
{
    private readonly EmbeddedSampleLabTestRepository _repository = new();

    /// <summary>
    /// Tres protocolos vieram do recorte em markdown das views e tres foram transcritos da
    /// grade do diagrama, que e onde moram os casos que o recorte nao tem: laudo sem numero
    /// de serie, laudo com os tres gases de Duval nao detectados e resultado que nenhuma
    /// estrategia de parse aceita.
    /// </summary>
    [Fact]
    public void MeasuredSampleCarriesTheSixObservedProtocols()
    {
        IReadOnlyList<SampleAnalysis> sample = EmbeddedSampleLabTestRepository.ReadMeasuredSample();

        Assert.Equal(
            [
                "01351/2026-004",
                "01417/2026-001",
                "01439/2026-004",
                "01482/2026-001",
                "01563/2026-018",
                "01718/2026-005"
            ],
            sample.Select(analysis => analysis.ProtocoloLactec).Order());
    }

    /// <summary>
    /// A serie de demonstracao acrescenta laudos, e nunca redefine um laudo medido. Um
    /// protocolo repetido nos dois arquivos entraria duas vezes na amostra com conteudos
    /// diferentes, e o grafico mostraria dois pontos na mesma data sem ninguem notar.
    /// </summary>
    [Fact]
    public void DemonstrationSeriesDoesNotRepeatAMeasuredProtocol()
    {
        IEnumerable<string> measured = EmbeddedSampleLabTestRepository.ReadMeasuredSample()
            .Select(analysis => analysis.ProtocoloLactec);

        IEnumerable<string> written = EmbeddedSampleLabTestRepository.ReadDemonstrationSeries()
            .Select(analysis => analysis.ProtocoloLactec);

        Assert.Empty(measured.Intersect(written, StringComparer.Ordinal));
    }

    /// <summary>
    /// A exportacao em markdown achatou <c>NULL</c> e texto vazio no mesmo campo vazio. O
    /// recurso embutido e JSON Lines justamente para o nulo continuar nulo, e os seis
    /// itens medidos fisico-quimicos do laudo real chegarem como ensaio nao realizado.
    /// </summary>
    [Fact]
    public void NullInTheSourceArrivesAsTestNotPerformed()
    {
        SampleAnalysis analysis = EmbeddedSampleLabTestRepository.ReadWholeSample()
            .Single(item => item.ProtocoloLactec == "01351/2026-004");

        Assert.Equal(6, analysis.Measurements.Count);
        Assert.All(analysis.Measurements, measurement =>
            Assert.IsType<MeasuredValue.TestNotPerformed>(measurement.Value));
    }

    [Fact]
    public void NonNumericSerialNumberArrivesWholeFromTheSource()
    {
        SampleAnalysis analysis = EmbeddedSampleLabTestRepository.ReadWholeSample()
            .Single(item => item.ProtocoloLactec == "01351/2026-004");

        Assert.Equal("111.594/1", analysis.SerialNumber);
    }

    [Fact]
    public void EachProtocolMapsToASingleAnalysis()
    {
        IReadOnlyList<SampleAnalysis> sample = EmbeddedSampleLabTestRepository.ReadWholeSample();

        Assert.Equal(sample.Count, sample.Select(analysis => analysis.ProtocoloLactec).Distinct().Count());
    }

    [Fact]
    public async Task AQueryFromAnotherTenantDoesNotSeeTheReport()
    {
        IReadOnlyList<SampleAnalysis> ofTheOwner = await _repository
            .GetAnalysesOfEquipmentAsync("EMPRESA DE ENERGIA", "571289", CancellationToken.None);
        IReadOnlyList<SampleAnalysis> ofAnother = await _repository
            .GetAnalysesOfEquipmentAsync("CONCORRENTE S.A.", "571289", CancellationToken.None);

        Assert.NotEmpty(ofTheOwner);
        Assert.Empty(ofAnother);
    }

    [Fact]
    public async Task AProtocolQueryFromAnotherTenantReturnsNothing()
    {
        SampleAnalysis? ofAnother = await _repository
            .GetAnalysisByProtocolAsync("CONCORRENTE S.A.", "01417/2026-001", CancellationToken.None);

        Assert.Null(ofAnother);
    }

    [Fact]
    public async Task AQueryWithoutATenantIsNotAccepted()
    {
        await Assert.ThrowsAsync<ArgumentException>(() => _repository
            .GetAnalysesOfEquipmentAsync(" ", "571289", CancellationToken.None));
    }

    [Fact]
    public async Task TheTenantQueryOnlyReturnsThatTenantsAnalyses()
    {
        IReadOnlyList<SampleAnalysis> ofTheTenant = await _repository
            .GetAnalysesOfTenantAsync("EMPRESA DE ENERGIA", CancellationToken.None);

        Assert.NotEmpty(ofTheTenant);
        Assert.All(ofTheTenant, analysis => Assert.Equal("EMPRESA DE ENERGIA", analysis.Company));
    }

    [Fact]
    public void AnItemNameOutsideTheKnownListFailsLoudly()
    {
        LabTestDataViewRow row = new()
        {
            Identificacao = "LACTEC-09999/2026",
            ProtocoloLactec = "09999/2026-001",
            NumSerie = "571289",
            Name = "Furfuraldeído",
            Resultado = "12",
            EmpresaDoTransformador = "EMPRESA DE ENERGIA",
            DataEmissao = new DateTime(2026, 6, 24),
            DataFinalAnalise = new DateTime(2026, 6, 23)
        };

        ResultNameOutsideKnownListException failure = Assert.Throws<ResultNameOutsideKnownListException>(
            () => EmbeddedSampleLabTestRepository.BuildAnalyses([row]));

        Assert.Equal("Furfuraldeído", failure.SourceName);
    }
}
