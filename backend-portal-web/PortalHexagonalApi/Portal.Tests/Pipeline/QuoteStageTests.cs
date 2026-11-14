using Portal.Application.Paging;
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
            new LaboratoryProgress("2026_45494", PipelineStage.DraftingReport));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_45494_V_1"),
            QuoteOf("quote-2", "EAQ_2026_45495_V_1"));

        await service.GetQuotesAsync(PageRequest.First, CancellationToken.None);

        Assert.Equal(1, laboratory.TimesTheListWasAsked);
        Assert.Equal(0, laboratory.TimesOneQuoteWasAsked);
        Assert.Equal(["2026_45494", "2026_45495"], laboratory.CodesAsked);
    }

    [Fact]
    public async Task TheLaboratoryStageTravelsBesideTheStageTheSourceWrote()
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("2026_45494", PipelineStage.DraftingReport));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_45494_V_1"));

        QuoteWithLaboratoryStage quote = Assert.Single(
            (await service.GetQuotesAsync(PageRequest.First, CancellationToken.None)).Items);

        Assert.Equal(PipelineStage.DraftingReport, quote.LaboratoryStage);
        Assert.Equal("Finalizado", quote.Quote.Stage);
    }

    [Fact]
    public async Task AQuoteTheLaboratoryDoesNotKnowKeepsOnlyItsCommercialStage()
    {
        InMemoryLaboratoryProgressRepository laboratory = new();
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_45482_V_1"));

        QuoteWithLaboratoryStage quote = Assert.Single(
            (await service.GetQuotesAsync(PageRequest.First, CancellationToken.None)).Items);

        Assert.Null(quote.LaboratoryStage);
        Assert.Equal("Finalizado", quote.Quote.Stage);
    }

    [Fact]
    public async Task AnEmptyQuoteListNeverAsksTheLaboratory()
    {
        InMemoryLaboratoryProgressRepository laboratory = new();
        QuoteService service = ServiceWith(laboratory);

        Assert.Empty((await service.GetQuotesAsync(PageRequest.First, CancellationToken.None)).Items);
        Assert.Equal(0, laboratory.TimesTheListWasAsked);
    }

    /// <summary>
    /// O adaptador do Salesforce mapeia codigo ausente para nulo. Perguntar por vazio
    /// gastaria uma consulta e, num adaptador que compare sem cuidado, casaria com qualquer
    /// linha sem OrcamentoCRM. Codigo fora do formato tambem nao vira pergunta: a regra nao
    /// procura por prefixo nem tenta adivinhar.
    /// </summary>
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("EAQ_2024_37569_V_")]
    [InlineData("EAQ_2024_37569_UHAB")]
    [InlineData("ORC-52")]
    public async Task AQuoteWithoutAValidQuotationCodeIsNotAskedAbout(string? quotationCode)
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("2024_37569", PipelineStage.DraftingReport));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2024_37569_V_1", quotationCode));

        QuoteWithLaboratoryStage listed = Assert.Single(
            (await service.GetQuotesAsync(PageRequest.First, CancellationToken.None)).Items);
        QuoteWithLaboratoryStage? detail = await service.GetQuoteByIdAsync("quote-1", CancellationToken.None);

        Assert.Null(listed.LaboratoryStage);
        Assert.Null(detail!.LaboratoryStage);
        Assert.Equal(0, laboratory.TimesTheListWasAsked);
        Assert.Equal(0, laboratory.TimesOneQuoteWasAsked);
    }

    /// <summary>
    /// O vinculo e o codigo da cotacao, e nunca o codigo publico (no Salesforce, o da
    /// oportunidade). As tres formas aceitas chegam ao laboratorio na forma nua, e a etapa
    /// que volta e devolvida ao orcamento pela mesma chave.
    /// </summary>
    [Theory]
    [InlineData("EAQ_2024_37569_V_1")]
    [InlineData("EAQ_2024_37569_V_12")]
    [InlineData("EAQ_2024_37569")]
    [InlineData("2024_37569")]
    [InlineData(" EAQ_2024_37569_V_1 ")]
    public async Task TheLaboratoryIsAskedByTheKeyOfTheQuotationCode(string quotationCode)
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("2024_37569", PipelineStage.DraftingReport));
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2099_99999_V_1", quotationCode));

        QuoteWithLaboratoryStage listed = Assert.Single(
            (await service.GetQuotesAsync(PageRequest.First, CancellationToken.None)).Items);
        QuoteWithLaboratoryStage? detail = await service.GetQuoteByIdAsync("quote-1", CancellationToken.None);

        Assert.Equal(PipelineStage.DraftingReport, listed.LaboratoryStage);
        Assert.Equal(PipelineStage.DraftingReport, detail!.LaboratoryStage);
        Assert.Equal(["2024_37569", "2024_37569"], laboratory.CodesAsked);
        Assert.Equal("EAQ_2099_99999_V_1", listed.Quote.Code);
    }

    [Fact]
    public async Task OnlyTheServedPageIsAskedAboutAndTheTotalIsKept()
    {
        InMemoryLaboratoryProgressRepository laboratory = new();
        QuoteService service = ServiceWith(laboratory, QuoteOf("quote-1", "EAQ_2026_00001_V_1"),
            QuoteOf("quote-2", "EAQ_2026_00002_V_1"), QuoteOf("quote-3", "EAQ_2026_00003_V_1"));

        var page = await service.GetQuotesAsync(new PageRequest(2, 2), CancellationToken.None);

        Assert.Equal("quote-3", Assert.Single(page.Items).Quote.Id);
        Assert.Equal(3, page.TotalCount);
        Assert.Equal(["2026_00003"], laboratory.CodesAsked);
    }

    [Fact]
    public async Task AskingForOneQuoteAsksTheLaboratoryAboutThatCodeOnly()
    {
        InMemoryLaboratoryProgressRepository laboratory = new(
            new LaboratoryProgress("2026_45495", PipelineStage.ReportPublished));
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
            new LaboratoryProgress("2026_45495", PipelineStage.ReportPublished));
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
            () => service.GetQuotesAsync(PageRequest.First, CancellationToken.None));
    }

    private static QuoteService ServiceWith(
        ILaboratoryProgressRepository laboratory,
        params Quote[] quotes)
    {
        return new QuoteService(new ScriptedQuoteRepository(quotes), new ScriptedDocuments(), laboratory);
    }

    private static Quote QuoteOf(string id, string code)
    {
        return QuoteOf(id, code, quotationCode: code);
    }

    private static Quote QuoteOf(string id, string code, string? quotationCode)
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
            createdDate: new DateTimeOffset(2026, 5, 5, 8, 30, 0, TimeSpan.Zero),
            quotationCode: quotationCode);
    }

    private sealed class ScriptedQuoteRepository(Quote[] quotes) : IQuoteRepository
    {
        public Task<Page<Quote>> GetQuotesAsync(PageRequest page, CancellationToken cancellationToken)
        {
            return Task.FromResult(Page<Quote>.Of(quotes, page));
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
