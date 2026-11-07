using Portal.Application.Pipeline;
using Portal.Application.Pipeline.Exceptions;
using Portal.Application.Quotes;
using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Pipeline;

/// <summary>
/// A aplicacao pela porta condutora, com duble nas duas portas conduzidas. O que estes
/// testes provam e a escolha, e nao o corpo HTTP: quais perguntas a aplicacao faz, quantas
/// vezes e com quais chaves. O teste de contrato prova o que o fio carrega; este prova o que
/// um adaptador de banco vai ter que executar.
/// </summary>
public sealed class QuoteStageTests
{
    [Fact]
    public async Task TheQuoteListAsksTheLaboratoryOnceWithEveryCode()
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("EAQ_2026_45494_V_1", PipelineStage.DraftingReport));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_45494_V_1"),
            QuoteOf("quote-2", "EAQ_2026_45495_V_1"));

        await service.GetQuotesAsync(CancellationToken.None);

        Assert.Equal(1, laboratory.TimesTheListWasAsked);
        Assert.Equal(0, laboratory.TimesOneQuoteWasAsked);
        Assert.Equal(["EAQ_2026_45494_V_1", "EAQ_2026_45495_V_1"], laboratory.CodesAsked);
    }

    [Fact]
    public async Task TheLaboratoryStageTravelsBesideTheStageTheSourceWrote()
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("EAQ_2026_45494_V_1", PipelineStage.DraftingReport));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_45494_V_1"));

        QuoteWithLaboratoryStage quote = Assert.Single(
            await service.GetQuotesAsync(CancellationToken.None));

        Assert.Equal(PipelineStage.DraftingReport, quote.LaboratoryStage);
        Assert.Equal("Finalizado", quote.Quote.Stage);
    }

    [Fact]
    public async Task AQuoteTheLaboratoryDoesNotKnowKeepsOnlyItsCommercialStage()
    {
        InMemoryLaboratoryProgressRepository laboratory = new();
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_45482_V_1"));

        QuoteWithLaboratoryStage quote = Assert.Single(
            await service.GetQuotesAsync(CancellationToken.None));

        Assert.Null(quote.LaboratoryStage);
        Assert.Equal("Finalizado", quote.Quote.Stage);
    }

    [Fact]
    public async Task AnEmptyQuoteListNeverAsksTheLaboratory()
    {
        InMemoryLaboratoryProgressRepository laboratory = new();
        QuoteService service = ServiceWith(laboratory);

        Assert.Empty(await service.GetQuotesAsync(CancellationToken.None));
        Assert.Equal(0, laboratory.TimesTheListWasAsked);
    }

    /// <summary>
    /// O adaptador do Salesforce mapeia codigo ausente para texto vazio. Perguntar por vazio
    /// gastaria uma consulta e, num adaptador que compare sem cuidado, casaria com qualquer
    /// linha sem OrcamentoCRM.
    /// </summary>
    [Fact]
    public async Task AQuoteWithoutACodeIsNotAskedAbout()
    {
        InMemoryLaboratoryProgressRepository laboratory = new();
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", ""));

        QuoteWithLaboratoryStage quote = Assert.Single(
            await service.GetQuotesAsync(CancellationToken.None));

        Assert.Null(quote.LaboratoryStage);
        Assert.Empty(laboratory.CodesAsked);
    }

    [Fact]
    public async Task AskingForOneQuoteAsksTheLaboratoryAboutThatCodeOnly()
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("EAQ_2026_45495_V_1", PipelineStage.ReportPublished));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-2", "EAQ_2026_45495_V_1"));

        QuoteWithLaboratoryStage? quote = await service.GetQuoteByIdAsync("quote-2", CancellationToken.None);

        Assert.Equal(PipelineStage.ReportPublished, quote!.LaboratoryStage);
        Assert.Equal(1, laboratory.TimesOneQuoteWasAsked);
        Assert.Equal(0, laboratory.TimesTheListWasAsked);
    }

    [Fact]
    public async Task DownloadingTheDocumentDoesNotAskTheLaboratory()
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("EAQ_2026_45495_V_1", PipelineStage.ReportPublished));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-2", "EAQ_2026_45495_V_1"));

        Assert.NotNull(await service.GetDocumentAsync("quote-2", CancellationToken.None));
        Assert.Equal(0, laboratory.TimesTheListWasAsked);
        Assert.Equal(0, laboratory.TimesOneQuoteWasAsked);
    }

    /// <summary>
    /// A queda do AutoLAB nao vira ausencia de noticia. Engolir a falha devolveria uma
    /// listagem que parece certa e que afirma, para todo orcamento em ensaio, que o
    /// laboratorio nao tem nada a dizer.
    /// </summary>
    [Fact]
    public async Task AFailureOfTheLaboratorySourceIsNotSwallowedIntoSilence()
    {
        InMemoryLaboratoryProgressRepository laboratory = new()
        {
            Failure = new LaboratoryProgressSourceUnavailableException("servidor interno fora do ar")
        };
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_45494_V_1"));

        await Assert.ThrowsAsync<LaboratoryProgressSourceUnavailableException>(
            () => service.GetQuotesAsync(CancellationToken.None));
    }

    private static QuoteService ServiceWith(
        ILaboratoryProgressRepository laboratory,
        params Quote[] quotes)
    {
        return new QuoteService(new ScriptedQuoteRepository(quotes), new ScriptedDocuments(), laboratory);
    }

    private static Quote QuoteOf(string id, string code)
    {
        return new Quote(
            id: id,
            opportunityId: "opportunity-" + id,
            code: code,
            name: "Pacote de ensaios",
            status: "Em Andamento",
            stage: "Finalizado",
            description: "Orcamento de teste",
            companyName: "EMPRESA DE ENERGIA",
            externalContactName: "Equipe Comercial",
            externalContactEmail: "comercial@exemplo.com",
            totalPrice: 1000m,
            createdDate: new DateTimeOffset(2026, 5, 5, 8, 30, 0, TimeSpan.Zero));
    }

    private sealed class ScriptedQuoteRepository(Quote[] quotes) : IQuoteRepository
    {
        public Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<IReadOnlyList<Quote>>([.. quotes]);
        }

        public Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
        {
            return Task.FromResult(quotes.FirstOrDefault(quote => quote.Id == id));
        }
    }

    private sealed class ScriptedDocuments : IQuoteDocumentRepository
    {
        public Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
        {
            return Task.FromResult<byte[]?>("%PDF-1.4 teste"u8.ToArray());
        }
    }
}
