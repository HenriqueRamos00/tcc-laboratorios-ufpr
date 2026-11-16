using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

using Portal.Adapters.PortalStore;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.PortalStore;

public sealed class ClientDecisionRepositoryTests : IDisposable
{
    private readonly SqliteConnection _connection = new("Data Source=:memory:");

    public ClientDecisionRepositoryTests()
    {
        _connection.Open();
        using PortalStoreDbContext database = Database();
        database.Database.EnsureCreated();
    }

    public void Dispose() => _connection.Dispose();

    [Fact]
    public async Task GravaOTermoEAOrdemDeCompraEDevolveSoOResumoDeCadaOrcamento()
    {
        ClientDecision acceptance = Decision("quote-3", ClientDecisionKind.Acceptance) with { Terms = Terms() };
        ClientDecision order = Decision("quote-6", ClientDecisionKind.PurchaseOrder)
            with { PurchaseOrder = new PurchaseOrderFile("oc.pdf", "%PDF-1.7 teste"u8.ToArray()) };

        await using (PortalStoreDbContext database = Database())
        {
            SqlServerClientDecisionRepository repository = new(database);
            await repository.AddAsync(acceptance, CancellationToken.None);
            await repository.AddAsync(order, CancellationToken.None);
        }

        await using PortalStoreDbContext reading = Database();
        IReadOnlyList<ClientDecision> summaries = await new SqlServerClientDecisionRepository(reading)
            .GetSummariesOfQuotesAsync(["quote-3"], CancellationToken.None);

        ClientDecision summary = Assert.Single(summaries);
        Assert.Equal((acceptance.Id, ClientDecisionKind.Acceptance), (summary.Id, summary.Kind));
        Assert.Null(summary.Terms);

        ClientDecisionRow stored = await reading.ClientDecisions
            .Include(row => row.Terms).Include(row => row.PurchaseOrder)
            .SingleAsync(row => row.QuoteId == "quote-6");
        Assert.Equal(order.PurchaseOrder!.Sha256, stored.PurchaseOrder!.Sha256);
        Assert.Equal(order.PurchaseOrder.Content, stored.PurchaseOrder.Content);
        Assert.Equal("Rua das Flores, 10", (await reading.Set<AcceptanceTermsRow>().SingleAsync()).BillingStreet);
    }

    [Fact]
    public async Task UmSegundoAceiteNoMesmoOrcamentoEConflitoEOEnvioTemVagaPropria()
    {
        await using PortalStoreDbContext database = Database();
        SqlServerClientDecisionRepository repository = new(database);

        await repository.AddAsync(Decision("quote-3", ClientDecisionKind.Acceptance), CancellationToken.None);
        await repository.AddAsync(Decision("quote-3", ClientDecisionKind.SampleShipment), CancellationToken.None);

        await Assert.ThrowsAsync<ClientDecisionConflictException>(() =>
            repository.AddAsync(Decision("quote-3", ClientDecisionKind.Rejection), CancellationToken.None));
    }

    private PortalStoreDbContext Database() =>
        new(new DbContextOptionsBuilder<PortalStoreDbContext>().UseSqlite(_connection).Options);

    private static ClientDecision Decision(string quoteId, ClientDecisionKind kind) => new(
        Guid.NewGuid(), quoteId, "EAQ_2026_45484_V_1", kind,
        new ClientDecisionAuthor("42", "Marina Costa", "marina@empresa.com"),
        new DateTimeOffset(2026, 11, 15, 12, 0, 0, TimeSpan.Zero));

    private static AcceptanceTerms Terms() => new(
        new BillingAddress("EMPRESA DE ENERGIA", "30960572000183", "", "Rua das Flores, 10", "Curitiba", "PR",
            "Centro", "80000000", "Marina", "41999999999", "financeiro@empresa.com"),
        new AcceptanceApproval("Marina Costa", "Joao Lima", "Engenheira", "Manutencao",
            new DateOnly(2026, 11, 15), true, "laudos@empresa.com"));
}
