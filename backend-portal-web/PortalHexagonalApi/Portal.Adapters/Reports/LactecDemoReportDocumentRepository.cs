using System.Globalization;
using System.Text;

using Portal.Application.Paging;
using Portal.Application.Quotes;
using Portal.Application.Reports;
using Portal.Domain.Reports;
using Portal.Domain.Quotes;

namespace Portal.Adapters.Reports;

/// <summary>
/// Massa documental legada do Portal do Cliente para a fonte Lactec. Os relatórios e PDFs são
/// exemplos, não uma integração real com o AutoLAB. A lista respeita os orçamentos autorizados.
/// Substituição pela integração real: https://github.com/HenriqueRamos00/tcc-laboratorios-ufpr/issues/86.
/// </summary>
public sealed class LactecDemoReportDocumentRepository(IQuoteRepository quotes) : IReportDocumentRepository
{
    // Um PDF de uma página, suficiente para o navegador abrir o arquivo baixado
    // enquanto o AutoLAB não serve o laudo de verdade.
    private static readonly byte[] ExamplePdf = Encoding.ASCII.GetBytes(string.Join('\n',
    [
        "%PDF-1.4",
        "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj",
        "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj",
        "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >> endobj",
        "trailer << /Root 1 0 R >>",
        "%%EOF"
    ]));

    // Massa de demonstração enquanto o AutoLAB não publica o laudo pelo portal. Os
    // identificadores de orçamento são os mesmos que o repositório de orçamentos entrega na
    // etapa "Relatório publicado": é esse vínculo que faz a tela de um orçamento mostrar os
    // laudos dele, e não os de todo mundo.
    private static readonly IReadOnlyList<DemoConclusion> Conclusions =
    [
        new(
            "quote-15",
            "EAQ_2026_45496_V_1",
            "00307/2026",
            "Ensaios Físico-Químicos em Óleo Isolante",
            "O processo de análise foi concluído com sucesso. Todos os parâmetros técnicos foram " +
                "validados pela curadoria laboratorial e o laudo final está disponível para visualização.",
            [
                Report("relatorio-00307-1", "LACTEC-00307/2026", "00307/2026", "Transformador TR-01", "Lab. de Óleos", "07/03/2026"),
                Report("relatorio-00307-2", "LACTEC-00308/2026", "00307/2026", "Transformador TR-02", "Lab. de Óleos", "07/03/2026"),
                Report("relatorio-00307-3", "LACTEC-00309/2026", "00307/2026", "Transformador TR-03", "Lab. de Óleos", "09/03/2026")
            ]),
        new(
            "quote-18",
            "EAQ_2026_45499_V_1",
            "00412/2026",
            "Cromatografia de Gases Dissolvidos",
            "A cromatografia dos quatro transformadores foi concluída e validada pela curadoria " +
                "laboratorial. Os laudos estão disponíveis para visualização e download.",
            [
                Report("relatorio-00412-1", "LACTEC-00412/2026", "00412/2026", "Transformador TR-11", "Lab. de Óleos", "22/08/2026"),
                Report("relatorio-00412-2", "LACTEC-00413/2026", "00412/2026", "Transformador TR-12", "Lab. de Óleos", "22/08/2026"),
                Report("relatorio-00412-3", "LACTEC-00414/2026", "00412/2026", "Transformador TR-13", "Lab. de Óleos", "24/08/2026"),
                Report("relatorio-00412-4", "LACTEC-00415/2026", "00412/2026", "Transformador TR-14", "Lab. de Óleos", "24/08/2026")
            ]),
        new(
            "quote-19",
            "EAQ_2026_45500_V_1",
            "00388/2026",
            "Ensaio de Rigidez Dielétrica",
            "A rigidez dielétrica das amostras foi medida e validada pela curadoria laboratorial. " +
                "Os laudos estão disponíveis para visualização e download.",
            [
                Report("relatorio-00388-1", "LACTEC-00388/2026", "00388/2026", "Óleo mineral isolante, tanque 1", "Lab. de Óleos", "06/08/2026"),
                Report("relatorio-00388-2", "LACTEC-00389/2026", "00388/2026", "Óleo mineral isolante, tanque 2", "Lab. de Óleos", "06/08/2026")
            ]),
        // Orçamento da antiga massa local; só aparece se a API autorizar esse identificador.
        new(
            "EAQ_2026_45490_V_3",
            "EAQ_2026_45490_V_3",
            "00501/2026",
            "Análise Físico-Química e Microbiológica de Água",
            "A análise da água dos dois pontos de coleta foi concluída e validada pela curadoria " +
                "laboratorial. Os laudos estão disponíveis para visualização e download.",
            [
                Report("relatorio-00501-1", "LACTEC-00501/2026", "00501/2026", "Água, ponto de coleta 1", "Lab. de Águas", "21/10/2026"),
                Report("relatorio-00501-2", "LACTEC-00502/2026", "00501/2026", "Água, ponto de coleta 2", "Lab. de Águas", "21/10/2026")
            ])
    ];

    public async Task<IReadOnlyList<PublishedReport>> GetPublishedAsync(CancellationToken cancellationToken)
    {
        // A fonte de orcamentos e paginada; a massa percorre as paginas do cliente para manter
        // a ordem dos orcamentos autorizados.
        List<PublishedReport> published = [];
        Page<Quote> page;
        int number = 1;
        do
        {
            page = await quotes.GetQuotesAsync(new PageRequest(number++, PageRequest.MaxPageSize), cancellationToken);
            published.AddRange(page.Items.SelectMany(quote => Conclusions
                .FirstOrDefault(conclusion => conclusion.QuoteId == quote.Id)?.Reports
                ?? Array.Empty<PublishedReport>()));
        }
        while (page.HasNext && page.Items.Count > 0 && number <= PageRequest.MaxPage);

        return [.. published];
    }

    public Task<QuotePublishedReports> GetPublishedOfQuoteAsync(
        string quoteId,
        CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        DemoConclusion? conclusion = Conclusions.FirstOrDefault(item => item.QuoteId == quoteId);
        return Task.FromResult(conclusion is null
            ? new QuotePublishedReports(string.Empty, string.Empty, string.Empty, [])
            : new QuotePublishedReports(
                conclusion.Protocol,
                conclusion.TestType,
                conclusion.Summary,
                conclusion.Reports));
    }

    public async Task<PublishedReportFile?> GetFileAsync(
        string reportId,
        CancellationToken cancellationToken)
    {
        DemoConclusion? owner = Conclusions.FirstOrDefault(conclusion =>
            conclusion.Reports.Any(report => report.Id == reportId));
        if (owner is null) return null;

        if (await quotes.GetQuoteByIdAsync(owner.QuoteId, cancellationToken) is null)
            return null;

        return new PublishedReportFile((byte[])ExamplePdf.Clone());
    }

    private static PublishedReport Report(
        string id,
        string code,
        string protocol,
        string sample,
        string laboratory,
        string publishedOn)
    {
        DateTimeOffset date = DateTimeOffset.ParseExact(
            publishedOn,
            "dd/MM/yyyy",
            CultureInfo.InvariantCulture,
            DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal);

        return new PublishedReport(id, code, protocol, sample, laboratory, date);
    }

    private sealed record DemoConclusion(
        string QuoteId,
        string QuoteCode,
        string Protocol,
        string TestType,
        string Summary,
        IReadOnlyList<PublishedReport> Reports);
}
