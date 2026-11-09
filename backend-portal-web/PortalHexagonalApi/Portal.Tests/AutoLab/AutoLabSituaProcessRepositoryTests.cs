using Portal.Adapters.AutoLab;
using Portal.Adapters.Pipeline;
using Portal.Domain.Pipeline;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabSituaProcessRepositoryTests : IDisposable
{
    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Fact]
    public void TheBitColumnsBecomeOneAndZeroForTheReading()
    {
        SituaProcessRow row = AutoLabSituaProcessRepository.ToRow(new SituaProcessRecord
        {
            OrcamentoCRM = "2026_49001",
            Recebido = true,
            Protocolado = false,
            SituacaoEnsaios = null,
            SolicitacaoRelatorio = 2
        });

        Assert.Equal(1, row.Recebido);
        Assert.Equal(0, row.Protocolado);
        Assert.Null(row.SituacaoEnsaios);
        Assert.Equal(2, row.SolicitacaoRelatorio);
    }

    [Fact]
    public async Task TheStageReadFromTheDatabaseIsTheStageTheReadingGivesTheSameRows()
    {
        _database.AddSituaProcess("2026_49001", true, true, 3, 2);
        _database.AddSituaProcess("2026_49003", false, false, 1, 0);
        _database.AddSituaProcess("2026_49004", null, null, null, 0);
        _database.AddSituaProcess("2026_49999", true, true, 5, 2);

        string[] codes = ["2026_49001", "2026_49003", "2026_49004"];

        IReadOnlyList<LaboratoryProgress> found = await Repository()
            .GetProgressOfQuotesAsync(codes, CancellationToken.None);

        IReadOnlyList<LaboratoryProgress> expected = SituaProcessReading.ProgressOf(
            [
                new SituaProcessRow { OrcamentoCRM = "2026_49001", Recebido = 1, Protocolado = 1, SituacaoEnsaios = 3, SolicitacaoRelatorio = 2 },
                new SituaProcessRow { OrcamentoCRM = "2026_49003", Recebido = 0, Protocolado = 0, SituacaoEnsaios = 1, SolicitacaoRelatorio = 0 },
                new SituaProcessRow { OrcamentoCRM = "2026_49004", Recebido = null, Protocolado = null, SituacaoEnsaios = null, SolicitacaoRelatorio = 0 }
            ],
            codes);

        Assert.Equal(
            expected.Select(progress => (progress.QuoteCode, progress.Stage)).Order(),
            found.Select(progress => (progress.QuoteCode, progress.Stage)).Order());
        Assert.DoesNotContain(found, progress => progress.QuoteCode == "2026_49999");
        Assert.Contains(" IN (", Assert.Single(_database.Commands), StringComparison.Ordinal);
    }

    [Fact]
    public async Task OneQuoteIsAskedByItsWholeCodeAndNotByPrefix()
    {
        _database.AddSituaProcess("2026_49001_UHAB Agua Boa", true, true, 0, 0);

        Assert.Null(await Repository().GetProgressOfQuoteAsync("2026_49001", CancellationToken.None));
    }

    private AutoLabSituaProcessRepository Repository()
    {
        return new AutoLabSituaProcessRepository(_database.NewContext(), new AutoLabQueryLimits());
    }
}
