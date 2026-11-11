using System.Globalization;
using System.Text;

using Portal.Adapters.Reports;
using Portal.Application.Paging;
using Portal.Application.Quotes;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Reports;

public sealed class LactecDemoReportDocumentRepositoryTests
{
    [Fact]
    public async Task ListaMantemAmostrasDemonstrativasSoParaOrcamentosDaSessao()
    {
        var quotes = new AuthorizedQuotes(Quote("quote-15"));
        LactecDemoReportDocumentRepository repository = new(quotes);

        var reports = await repository.GetPublishedAsync(CancellationToken.None);

        Assert.Equal(3, reports.Count);
        Assert.Contains(reports, report => report.Code == "LACTEC-00307/2026");
        Assert.All(reports, report => Assert.Equal("00307/2026", report.Protocol));
        Assert.All(reports, report => Assert.Null(report.PublishedAt));
        Assert.Equal("07/03/2026", reports[0].PublishedOn.ToString("dd/MM/yyyy", CultureInfo.InvariantCulture));
    }

    [Fact]
    public async Task ListaSegueAOrdemDosOrcamentosAutorizados()
    {
        var quotes = new AuthorizedQuotes(Quote("quote-19"), Quote("quote-15"));
        LactecDemoReportDocumentRepository repository = new(quotes);

        var reports = await repository.GetPublishedAsync(CancellationToken.None);

        Assert.Equal(
            [
                "LACTEC-00388/2026",
                "LACTEC-00389/2026",
                "LACTEC-00307/2026",
                "LACTEC-00308/2026",
                "LACTEC-00309/2026"
            ],
            reports.Select(report => report.Code));
    }

    [Fact]
    public async Task ArquivoDemonstrativoDependeDoVinculoAtualDoOrcamento()
    {
        LactecDemoReportDocumentRepository authorized = new(new AuthorizedQuotes(Quote("quote-15")));
        LactecDemoReportDocumentRepository outsideCompany = new(new AuthorizedQuotes());

        var availableFile = await authorized.GetFileAsync("relatorio-00307-1", CancellationToken.None);
        var inaccessibleFile = await outsideCompany.GetFileAsync("relatorio-00307-1", CancellationToken.None);

        Assert.NotNull(availableFile);
        Assert.StartsWith("%PDF-1.4", Encoding.ASCII.GetString(availableFile.Content));
        Assert.Null(inaccessibleFile);
    }

    private static Quote Quote(string id) => new(
        id,
        "opportunity-15",
        "EAQ_2026_45496_V_1",
        "Análise físico-química de óleo isolante",
        "Finalizado",
        "Relatório publicado",
        "Proposta de demonstração",
        "Empresa Exemplo",
        "Pessoa de contato",
        "contato@example.test",
        1000m,
        DateTimeOffset.Parse("2026-11-07T16:10:00Z"));

    private sealed class AuthorizedQuotes(params Quote[] visibleQuotes) : IQuoteRepository
    {
        public Task<Page<Quote>> GetQuotesAsync(PageRequest page, CancellationToken cancellationToken) =>
            Task.FromResult(Page<Quote>.Of(visibleQuotes, page));

        public Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken) =>
            Task.FromResult(visibleQuotes.FirstOrDefault(quote => quote.Id == id));
    }
}
