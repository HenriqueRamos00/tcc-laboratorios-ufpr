using Portal.Application.LabResults;
using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

/// <summary>
/// Entra pela porta condutora com um dublê atras da porta conduzida: nenhum servidor,
/// nenhum HTTP e nenhum adaptador de producao. E a unica forma de medir o que a aplicacao
/// decide, em vez de medir o que a amostra embutida por acaso contem.
///
/// Os testes de contrato continuam existindo e entram pelo outro lado, pelo HTTP com o
/// adaptador real. Os dois caminhos sao necessarios: o contrato prova o que o fio carrega,
/// e este aqui prova a escolha que ninguem ve pelo fio.
/// </summary>
public sealed class LabResultsConsultationTests
{
    private const string Tenant = "EMPRESA DE ENERGIA";
    private const string OtherTenant = "OUTRA EMPRESA S.A.";

    /// <summary>
    /// A listagem e o unico lugar em que perguntar o inquilino inteiro e a pergunta certa:
    /// o AutoLAB nao tem cadastro de equipamento, entao a lista nasce das analises.
    /// </summary>
    [Fact]
    public async Task TheListingGathersOneEquipmentPerKeyAcrossTheTenant()
    {
        InMemoryLabTestRepository source = new(
            AnalysisOf("01351/2026-004", "111.594/1"),
            AnalysisOf("01352/2026-004", "111.594/1"),
            AnalysisOf("01417/2026-001", "571289"));

        LabResultsConsultation service = new(source);

        IReadOnlyList<TestedEquipment> found = await service.ListEquipmentAsync(
            Tenant, CancellationToken.None);

        Assert.Equal(["111.594/1", "571289"], found.Select(equipment => equipment.EquipmentKey).Order());
        Assert.Equal(1, source.TimesTheWholeTenantWasAsked);
    }

    /// <summary>
    /// O detalhe reusa a consulta por equipamento. Filtrar a lista inteira na memoria daria a
    /// mesma resposta e obrigaria um adaptador de banco a trazer o cliente todo para descartar
    /// quase tudo; sem este caso, a troca passa com a suite verde.
    /// </summary>
    [Fact]
    public async Task TheDetailAsksForOneEquipmentAndNeverForTheWholeTenant()
    {
        InMemoryLabTestRepository source = new(
            AnalysisOf("01351/2026-004", "111.594/1"),
            AnalysisOf("01417/2026-001", "571289"));

        LabResultsConsultation service = new(source);

        TestedEquipment? found = await service.GetEquipmentAsync(
            Tenant, "571289", CancellationToken.None);

        Assert.NotNull(found);
        Assert.Equal("571289", found.EquipmentKey);
        Assert.Equal(1, source.TimesOneEquipmentWasAsked);
        Assert.Equal(0, source.TimesTheWholeTenantWasAsked);
    }

    [Fact]
    public async Task AnEquipmentOfAnotherTenantIsNotListedNorFound()
    {
        InMemoryLabTestRepository source = new(
            AnalysisOf("01351/2026-004", "111.594/1", company: OtherTenant));

        LabResultsConsultation service = new(source);

        Assert.Null(await service.GetEquipmentAsync(Tenant, "111.594/1", CancellationToken.None));
        Assert.Empty(await service.ListEquipmentAsync(Tenant, CancellationToken.None));
    }

    /// <summary>
    /// Perguntar o inquilino inteiro e filtrar na memoria devolve exatamente a mesma resposta,
    /// e e por isso que nenhum teste de contrato percebe a troca. Um adaptador de banco com
    /// essa escolha traz todos os laudos do cliente para devolver os de um transformador so.
    /// </summary>
    [Fact]
    public async Task TheServiceAsksForOneEquipmentAndNeverForTheWholeTenant()
    {
        InMemoryLabTestRepository source = new(
            AnalysisOf("01351/2026-004", "111.594/1"),
            AnalysisOf("01417/2026-001", "571289"));

        LabResultsConsultation service = new(source);

        await service.GetHealthIndicatorsAsync(Query("111.594/1"), CancellationToken.None);

        Assert.Equal(1, source.TimesOneEquipmentWasAsked);
        Assert.Equal(0, source.TimesTheWholeTenantWasAsked);
    }

    [Fact]
    public async Task AnEquipmentOfAnotherTenantIsNotFound()
    {
        InMemoryLabTestRepository source = new(
            AnalysisOf("01351/2026-004", "111.594/1", company: OtherTenant));

        LabResultsConsultation service = new(source);

        Assert.Null(await service.GetHealthIndicatorsAsync(Query("111.594/1"), CancellationToken.None));
    }

    /// <summary>
    /// A terceira operacao da porta e a unica capaz de responder quando o laudo pedido na URL
    /// nao esta na serie carregada. O adaptador embutido carrega a amostra inteira e por isso
    /// nunca a exercita; com o dublê ela fica viva, que e o caso de um adaptador paginado.
    /// </summary>
    [Fact]
    public async Task AReportOutsideTheLoadedSeriesIsFetchedByProtocol()
    {
        SampleAnalysis inTheSeries = AnalysisOf("01351/2026-004", "111.594/1");
        SampleAnalysis apart = AnalysisOf("01352/2026-004", "111.594/1");

        InMemoryLabTestRepository source = new(inTheSeries);
        PaginatedSource paginated = new(source, apart);

        LabResultsConsultation service = new(paginated);

        SampleHealthIndicators? indicators = await service.GetHealthIndicatorsAsync(
            Query("111.594/1", selected: "01352/2026-004"),
            CancellationToken.None);

        Assert.NotNull(indicators);
        Assert.NotNull(indicators.SelectedAnalysis);
        Assert.Equal("01352/2026-004", indicators.SelectedAnalysis.ProtocoloLactec);
        Assert.Equal(1, source.TimesOneProtocolWasAsked);
    }

    /// <summary>
    /// Um protocolo valido de outro transformador nomearia a migalha de pao de uma serie que
    /// nao e a dele. A comparacao e pela chave do equipamento, nunca pelo numero de serie cru.
    /// </summary>
    [Fact]
    public async Task AReportOfAnotherEquipmentIsNotAcceptedAsTheSelectedOne()
    {
        SampleAnalysis inTheSeries = AnalysisOf("01351/2026-004", "111.594/1");
        SampleAnalysis ofAnother = AnalysisOf("01417/2026-001", "571289");

        LabResultsConsultation service = new(
            new PaginatedSource(new InMemoryLabTestRepository(inTheSeries), ofAnother));

        SampleHealthIndicators? indicators = await service.GetHealthIndicatorsAsync(
            Query("111.594/1", selected: "01417/2026-001"),
            CancellationToken.None);

        Assert.NotNull(indicators);
        Assert.Null(indicators.SelectedAnalysis);
    }

    [Fact]
    public async Task AConsultationWithoutTenantIsRefusedBeforeTheSourceIsTouched()
    {
        InMemoryLabTestRepository source = new(AnalysisOf("01351/2026-004", "111.594/1"));
        LabResultsConsultation service = new(source);

        await Assert.ThrowsAsync<ArgumentException>(() => service.GetHealthIndicatorsAsync(
            new HealthIndicatorsQuery("   ", "111.594/1", null),
            CancellationToken.None));

        Assert.Equal(0, source.TimesOneEquipmentWasAsked);
    }

    private static HealthIndicatorsQuery Query(string equipmentKey, string? selected = null)
    {
        return new HealthIndicatorsQuery(Tenant, equipmentKey, selected);
    }

    private static SampleAnalysis AnalysisOf(
        string protocoloLactec,
        string serialNumber,
        string company = Tenant)
    {
        return AnalysisBuilder.Valid(
            protocoloLactec: protocoloLactec,
            serialNumber: serialNumber,
            company: company);
    }

    /// <summary>
    /// Uma fonte que so devolve na consulta por equipamento o que ja esta carregado, e guarda
    /// o resto atras da consulta por protocolo. E o comportamento de um adaptador paginado,
    /// e o adaptador embutido de hoje nao consegue imita-lo porque carrega tudo de uma vez.
    /// </summary>
    private sealed class PaginatedSource : ILabTestRepository
    {
        private readonly InMemoryLabTestRepository _loaded;
        private readonly InMemoryLabTestRepository _apart;

        public PaginatedSource(InMemoryLabTestRepository loaded, params SampleAnalysis[] apart)
        {
            _loaded = loaded;
            _apart = new InMemoryLabTestRepository(apart);
        }

        public Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfTenantAsync(
            string tenant,
            CancellationToken cancellationToken)
        {
            return _loaded.GetAnalysesOfTenantAsync(tenant, cancellationToken);
        }

        public Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
            string tenant,
            string equipmentKey,
            CancellationToken cancellationToken)
        {
            return _loaded.GetAnalysesOfEquipmentAsync(tenant, equipmentKey, cancellationToken);
        }

        public async Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
            string tenant,
            string protocoloLactec,
            CancellationToken cancellationToken)
        {
            return await _loaded.GetAnalysisByProtocolAsync(tenant, protocoloLactec, cancellationToken)
                ?? await _apart.GetAnalysisByProtocolAsync(tenant, protocoloLactec, cancellationToken);
        }
    }
}
