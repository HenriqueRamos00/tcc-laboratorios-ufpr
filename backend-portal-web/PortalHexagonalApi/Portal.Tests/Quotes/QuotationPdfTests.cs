using Portal.Adapters.Quotes;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

/// <summary>
/// O documento gerado, medido pelo que da para medir sem fragilidade: quantas paginas tem,
/// em qual delas cada texto aparece e como o numero foi formatado. Nada aqui afirma sobre
/// pixel, posicao ou fonte, e nada aqui testa a QuestPDF: o que esta sob teste e a decisao de
/// desenho, e nao a biblioteca que a executa.
///
/// A massa e escrita a mao, com empresa generica. Nenhum valor do documento real do cliente
/// entra na suite.
/// </summary>
public sealed class QuotationPdfTests
{
    [Fact]
    public void OrcamentoSemItensSaiNasCincoPaginasDoDocumentoEmitido()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        Assert.Equal(5, PdfText.PageCountOf(document));
    }

    [Fact]
    public void ONumeroDoOrcamentoSaiNaPrimeiraPagina()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        IReadOnlyList<string> pages = PdfText.OfEachPage(document);

        Assert.Contains("EAQ_2026_00001_V_1", pages[0]);
    }

    [Fact]
    public void ARazaoSocialDoClienteSaiNaPrimeiraPagina()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        IReadOnlyList<string> pages = PdfText.OfEachPage(document);

        Assert.Contains("INDUSTRIADEEQUIPAMENTOS", PdfText.Collapsed(pages[0]));
    }

    [Fact]
    public void OValorTotalSaiFormatadoEmReais()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        string text = PdfText.Collapsed(PdfText.OfWholeDocument(document));

        Assert.Contains("R$11.270,00", text);
    }

    /// <summary>
    /// A secao 2 e uma tabela, e a tabela existe mesmo sem item. O cabecalho e o valor total
    /// continuam, e o documento diz na propria tabela que a relacao nao e publicada: somar
    /// itens que ninguem informou seria inventar valor.
    /// </summary>
    [Fact]
    public void OrcamentoSemItensMantemACabecaDaTabelaEOValorTotal()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        string text = PdfText.Collapsed(PdfText.OfWholeDocument(document));

        Assert.Contains("Norma/Procedimento", text);
        Assert.Contains("Valortotal", text);
    }

    [Fact]
    public void OrcamentoComItensSomaCadaLinhaEGanhaAsLinhasNaTabela()
    {
        QuotationItem[] items =
        [
            new("Ensaio de rigidez dielétrica", "NBR 6869", 2m, 810.34m),
            new("Ensaio de teor de água", "NBR 10710", 1m, 1_200m)
        ];

        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), items);

        string text = PdfText.Collapsed(PdfText.OfWholeDocument(document));

        Assert.Contains("R$1.620,68", text);
        Assert.Contains("R$1.200,00", text);
    }

    [Fact]
    public void ORodapeApareceEmTodaPagina()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        IReadOnlyList<string> pages = PdfText.OfEachPage(document);

        Assert.All(pages, page => Assert.Contains("DOC-05_Rev.05", page));
    }

    [Fact]
    public void ANumeracaoDeCadaPaginaContaOTotalDoDocumento()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        IReadOnlyList<string> pages = PdfText.OfEachPage(document);

        Assert.Equal(5, pages.Count);
        Assert.Contains("1de5", PdfText.Collapsed(pages[0]));
        Assert.Contains("5de5", PdfText.Collapsed(pages[4]));
    }

    [Fact]
    public void APaginaInstitucionalEAUltimaEExisteSemNenhumDadoDoOrcamento()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        IReadOnlyList<string> pages = PdfText.OfEachPage(document);

        Assert.Contains("Lactec", pages[^1]);
        Assert.DoesNotContain("EAQ_2026_00001_V_1", pages[^1]);
    }

    /// <summary>
    /// Orcamento sem data de criacao e sem descricao nao derruba o desenho: o rotulo fica e o
    /// valor sai em branco, porque apagar a linha mudaria a caixa e preencher seria inventar.
    /// </summary>
    [Fact]
    public void OrcamentoSemDataESemDescricaoContinuaGerandoODocumento()
    {
        Quote bare = new(
            id: "quote-sem-data",
            opportunityId: "opportunity-sem-data",
            code: "EAQ_2026_00002_V_1",
            name: "Proposta sem data",
            status: "Pendente de Aceite",
            stage: "Negociação",
            description: string.Empty,
            companyName: "INDUSTRIA DE EQUIPAMENTOS",
            externalContactName: "Equipe Comercial",
            externalContactEmail: "comercial@example.test",
            totalPrice: 0m,
            createdDate: null);

        byte[] document = QuotationPdf.Write(bare, []);

        Assert.Equal(5, PdfText.PageCountOf(document));
        Assert.Contains("R$0,00", PdfText.Collapsed(PdfText.OfWholeDocument(document)));
    }

    /// <summary>
    /// O dono da oportunidade e do lado do Lactec, e nao o contato do cliente. Ele nao entra
    /// no bloco do cliente, e enquanto nao houver contato do cliente na porta ele nao entra
    /// no documento em lugar nenhum.
    /// </summary>
    [Fact]
    public void ODonoDaOportunidadeNaoEPublicadoComoContatoDoCliente()
    {
        byte[] document = QuotationPdf.Write(QuoteWrittenByHand(), []);

        string text = PdfText.Collapsed(PdfText.OfWholeDocument(document));

        Assert.DoesNotContain("comercial@example.test", text);
        Assert.DoesNotContain("EquipeComercial", text);
    }

    /// <summary>
    /// O documento declara a data no fuso de quem emite, e nao no deslocamento em que a fonte
    /// mandou o instante.
    ///
    /// O Salesforce manda com deslocamento zero, e a tela imprime a mesma data no fuso do
    /// navegador. Sem converter, um orcamento criado as 22 horas de Brasilia sai com um dia na
    /// tela e com o dia seguinte no PDF que a propria tela oferece para baixar, e nenhum dos
    /// dois esta obviamente errado para quem olha so um deles.
    ///
    /// A fronteira escolhida e 01:00 em deslocamento zero, que e 22:00 do dia anterior em
    /// Brasilia: e o caso que distingue as duas leituras.
    /// </summary>
    [Fact]
    public void ADataDeEmissaoSaiNoDiaDeQuemEmiteENaoNoDiaDoDeslocamentoDaFonte()
    {
        Quote criadoDepoisDasNoveDaNoite = QuoteWrittenByHand(
            createdDate: new DateTimeOffset(2026, 11, 20, 1, 0, 0, TimeSpan.Zero));

        byte[] document = QuotationPdf.Write(criadoDepoisDasNoveDaNoite, []);

        IReadOnlyList<string> pages = PdfText.OfEachPage(document);

        Assert.Contains("19/11/2026", pages[0]);
        Assert.DoesNotContain("20/11/2026", pages[0]);
    }

    private static Quote QuoteWrittenByHand() => new(
        id: "quote-escrito-a-mao",
        opportunityId: "opportunity-escrita-a-mao",
        code: "EAQ_2026_00001_V_1",
        name: "Ensaios elétricos em amostras de polímero",
        status: "Pendente de Aceite",
        stage: "Negociação",
        description: "Ensaios mecânicos e dielétricos em amostras de polietileno.",
        companyName: "INDUSTRIA DE EQUIPAMENTOS",
        externalContactName: "Equipe Comercial",
        externalContactEmail: "comercial@example.test",
        totalPrice: 11_270.00m,
        createdDate: new DateTimeOffset(2026, 11, 3, 17, 37, 0, TimeSpan.Zero));

    private static Quote QuoteWrittenByHand(DateTimeOffset createdDate) => new(
        id: "quote-escrito-a-mao",
        opportunityId: "opportunity-escrita-a-mao",
        code: "EAQ_2026_00001_V_1",
        name: "Ensaios eletricos em amostras de polimero",
        status: "Pendente de Aceite",
        stage: "Negociação",
        description: "Ensaios mecanicos e dieletricos em amostras de polietileno.",
        companyName: "INDUSTRIA DE EQUIPAMENTOS",
        externalContactName: "Equipe Comercial",
        externalContactEmail: "comercial@example.test",
        totalPrice: 11_270.00m,
        createdDate: createdDate);
}
