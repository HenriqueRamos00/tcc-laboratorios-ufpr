using Portal.Application.Quotes;
using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

public sealed class FakeSalesforceQuoteRepository : IQuoteRepository
{
    private static readonly List<FakeQuote> Quotes =
    [
        new FakeQuote(
            Id: "quote-1",
            Quote: new Quote(
                id: "quote-1",
                code: "EAQ_2026_45482_V_1",
                name: "Análise físico-química de óleo isolante",
                status: "Pendente de Aceite",
                stage: "Qualificação",
                description: "Análise físico-química de óleo isolante",
                companyName: "Empresa Exemplo Alfa Ltda",
                externalContactName: "Fulano de Tal",
                externalContactEmail: "fulano@exemplo.com",
                totalPrice: 56292.30m,
                createdDate: new DateTimeOffset(2026, 1, 15, 9, 30, 0, TimeSpan.Zero))
        ),
        new FakeQuote(
            Id: "quote-2",
            Quote: new Quote(
                id: "quote-2",
                code: "EAQ_2026_45483_V_1",
                name: "Ensaios laboratoriais de materiais",
                status: "Em Andamento",
                stage: "Negociação",
                description: "Ensaios laboratoriais de materiais",
                companyName: "Empresa Exemplo Beta S.A.",
                externalContactName: "",
                externalContactEmail: "ciclano@exemplo.com",
                totalPrice: 12500.00m,
                createdDate: new DateTimeOffset(2026, 2, 3, 14, 0, 0, TimeSpan.Zero))
        )
    ];

    public Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
    {
        return Task.FromResult<IReadOnlyList<Quote>>(Quotes.Select(q => q.Quote).ToList());
    }

    public Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
    {
        Quote? quote = Quotes.FirstOrDefault(q => q.Id == id)?.Quote;
        return Task.FromResult(quote);
    }

    private sealed record FakeQuote(string Id, Quote Quote);
}
